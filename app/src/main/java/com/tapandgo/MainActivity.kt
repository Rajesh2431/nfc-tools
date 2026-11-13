package com.tapandgo

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.os.Bundle
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchFrom(intent)?.let { pendingLaunch.value = it }
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
        return messages.filterIsInstance<NdefMessage>().flatMap { it.records.toList() }
            .firstOrNull { it.tnf == NdefRecord.TNF_EXTERNAL_TYPE && it.type.toString(Charsets.US_ASCII) == RichRecords.TASK_TYPE }
            ?.payload?.toString(Charsets.US_ASCII)?.takeIf(RichRecords::isValidToken)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = settingsRepository.settings
        if (savedInstanceState == null) pendingLaunch.value = launchFrom(intent)
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
                TapAndGoApp(pendingLaunch = pendingLaunch.value, onLaunchConsumed = { pendingLaunch.value = null })
            }
        }
    }
}
