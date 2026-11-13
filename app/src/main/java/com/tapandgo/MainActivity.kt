package com.tapandgo

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
import com.tapandgo.R
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import com.tapandgo.core.ndef.RichRecords
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.data.settings.ThemeMode
import com.tapandgo.navigation.TapAndGoApp
import com.tapandgo.ui.theme.TapAndGoTheme

/** Single-activity host for the Compose UI; theme follows Settings. */
@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @javax.inject.Inject lateinit var settingsRepository: com.tapandgo.data.settings.SettingsRepository

    /** Trigger token (tag, shortcut, tile, other app) that launched or resumed the app; validated, never executed. */
    private val pendingLaunch = mutableStateOf<com.tapandgo.automation.LaunchRequest?>(null)

    /** tapandgo:// deep link received while running; the nav graph resolves it (screens only, no actions). */
    private val pendingDeepLink = mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchFrom(intent)?.let { pendingLaunch.value = it }
        if (intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "tapandgo") pendingDeepLink.value = intent
    }

    private fun launchFrom(intent: Intent?): com.tapandgo.automation.LaunchRequest? {
        if (intent == null) return null
        taskTokenFrom(intent)?.let { return com.tapandgo.automation.LaunchRequest(it, com.tapandgo.automation.RunSource.NFC_TAG) }
        return com.tapandgo.automation.WorkflowLaunch.parse(
            intent.action,
            intent.component?.className,
            intent.getStringExtra(com.tapandgo.automation.WorkflowLaunch.EXTRA_TOKEN),
            intent.getStringExtra(com.tapandgo.automation.WorkflowLaunch.EXTRA_SOURCE),
        )
    }

    /**
     * Extracts a task-tag token from an NDEF_DISCOVERED intent. Any app could
     * send such an intent, so the token is format-checked and only selects an
     * existing profile; auto-run can at most show in-app content or open a
     * Settings screen (ADR 0001).
     */
    private fun taskTokenFrom(intent: Intent?): String? {
        if (intent?.action != NfcAdapter.ACTION_NDEF_DISCOVERED) return null
        val messages = IntentCompat.getParcelableArrayExtra(intent, NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java) ?: return null
        return taskTokenIn(messages.filterIsInstance<NdefMessage>())
    }

    private fun taskTokenIn(messages: List<NdefMessage>): String? =
        messages.flatMap { it.records.toList() }
            .firstOrNull { it.tnf == NdefRecord.TNF_EXTERNAL_TYPE && it.type.toString(Charsets.US_ASCII) == RichRecords.TASK_TYPE }
            ?.payload?.toString(Charsets.US_ASCII)?.takeIf(RichRecords::isValidToken)

    // ---- App-wide reader mode -------------------------------------------------
    // While this activity is resumed, reader mode is on for every screen, so
    // Android never dispatches a tag to another app or the system tag viewer.
    // The current screen gets the tag through NfcTagRouter; with no screen
    // listening, a task tag runs its profile/workflow and any other tag opens Read.

    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var resumed = false

    /** Latest settings for code that runs outside Compose (reader callback, lifecycle). */
    @Volatile private var current = com.tapandgo.data.settings.AppSettings()
    private var settingsLoaded = false

    // ---- App lock ---------------------------------------------------------------
    private val locked = mutableStateOf(false)
    private val landing = mutableStateOf<String?>(null)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
    }
    private val lockFailed = mutableStateOf(false)
    private var stoppedAt = 0L

    /** Short grace period so file pickers, the camera and the lock prompt itself do not re-lock. */
    private val lockGraceMs = 30_000L

    private val confirmCredential = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) unlocked() else lockFailed.value = true
    }

    private fun unlocked() { locked.value = false; lockFailed.value = false; stoppedAt = 0L }

    private fun authenticate() {
        val keyguard = getSystemService(android.app.KeyguardManager::class.java)
        if (!keyguard.isDeviceSecure) { unlocked(); return } // screen lock removed: nothing to check against
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            val builder = android.hardware.biometrics.BiometricPrompt.Builder(this).setTitle(getString(R.string.applock_title))
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            } else {
                @Suppress("DEPRECATION") builder.setDeviceCredentialAllowed(true)
            }
            builder.build().authenticate(android.os.CancellationSignal(), mainExecutor, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) = unlocked()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { lockFailed.value = true }
            })
        } else {
            @Suppress("DEPRECATION")
            val i = keyguard.createConfirmDeviceCredentialIntent(getString(R.string.applock_title), getString(R.string.applock_detail))
            if (i == null) unlocked() else confirmCredential.launch(i)
        }
    }

    override fun onStart() {
        super.onStart()
        startPauseWatch()
        if (current.appLock && stoppedAt > 0 && android.os.SystemClock.elapsedRealtime() - stoppedAt > lockGraceMs) locked.value = true
    }

    private val onTag = NfcAdapter.ReaderCallback { tag ->
        if (locked.value) return@ReaderCallback // nothing is read or run while locked
        if (current.scanVibration) vibrate()
        if (com.tapandgo.core.nfc.NfcTagRouter.dispatch(tag)) return@ReaderCallback
        // Cached NDEF from discovery: no I/O, so nothing is held open here.
        val token = runCatching { android.nfc.tech.Ndef.get(tag)?.cachedNdefMessage }.getOrNull()?.let { taskTokenIn(listOf(it)) }
        runOnUiThread {
            if (token != null) {
                pendingLaunch.value = com.tapandgo.automation.LaunchRequest(token, com.tapandgo.automation.RunSource.NFC_TAG)
            } else {
                com.tapandgo.core.nfc.NfcTagRouter.park(tag)
            }
        }
    }

    private fun enableReader() {
        val a = adapter ?: return
        if (!resumed || !a.isEnabled) return
        if (com.tapandgo.core.nfc.NfcTagRouter.paused.value) { runCatching { a.disableReaderMode(this) }; return }
        val extras = Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        val flags = com.tapandgo.core.nfc.READER_FLAGS or (if (current.scanSound) 0 else NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS)
        runCatching { a.enableReaderMode(this, onTag, flags, extras) }
    }

    /** NFC switched on from Quick Settings while the app is open: arm reader mode immediately. */
    private val nfcState = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: Intent) {
            if (intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, 0) == NfcAdapter.STATE_ON) enableReader()
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        androidx.core.content.ContextCompat.registerReceiver(
            this, nfcState, android.content.IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        enableReader()
    }

    private var pauseWatch: kotlinx.coroutines.Job? = null

    private fun vibrate() {
        val v = if (android.os.Build.VERSION.SDK_INT >= 31) getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(android.os.Vibrator::class.java)
        runCatching { v?.vibrate(android.os.VibrationEffect.createOneShot(40, android.os.VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private fun startPauseWatch() {
        pauseWatch = lifecycleScope.let { scope ->
            scope.launch { com.tapandgo.core.nfc.NfcTagRouter.paused.collect { enableReader() } }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations) stoppedAt = android.os.SystemClock.elapsedRealtime()
        pauseWatch?.cancel()
        super.onStop()
    }

    override fun onPause() {
        resumed = false
        runCatching { unregisterReceiver(nfcState) }
        runCatching { adapter?.disableReaderMode(this) }
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = settingsRepository.settings
        if (savedInstanceState == null) pendingLaunch.value = launchFrom(intent)
        locked.value = savedInstanceState?.getBoolean(KEY_LOCKED) ?: false
        // Open on the chosen tab only for a plain launcher start (not tags, shortcuts or links).
        val useLanding = savedInstanceState == null && intent?.action == Intent.ACTION_MAIN && intent?.data == null
        lifecycleScope.launch {
            settingsFlow.collect { s ->
                val soundChanged = s.scanSound != current.scanSound
                current = s
                if (!settingsLoaded) {
                    settingsLoaded = true
                    if (s.appLock && savedInstanceState == null) locked.value = true
                    landing.value = if (useLanding) s.landingPage else null
                }
                if (soundChanged) enableReader()
            }
        }
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // System bar icons follow the app theme (it can differ from the system setting).
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            TapAndGoTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: false) {
                if (locked.value) {
                    com.tapandgo.ui.components.LockScreen(failed = lockFailed.value, onUnlock = ::authenticate)
                    androidx.compose.runtime.LaunchedEffect(Unit) { authenticate() }
                    return@TapAndGoTheme
                }
                TapAndGoApp(
                    landing = landing.value,
                    onLandingConsumed = { landing.value = null },
                    pendingLaunch = pendingLaunch.value,
                    onLaunchConsumed = { pendingLaunch.value = null },
                    pendingDeepLink = pendingDeepLink.value,
                    onDeepLinkConsumed = { pendingDeepLink.value = null },
                )
            }
        }
    }

    private companion object {
        const val KEY_LOCKED = "locked"
    }
}
