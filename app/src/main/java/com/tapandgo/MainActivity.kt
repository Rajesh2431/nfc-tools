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

    /** Token from a task tag that launched or resumed the app; validated, never executed. */
    private val pendingToken = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        taskTokenFrom(intent)?.let { pendingToken.value = it }
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
        pendingToken.value = taskTokenFrom(intent)
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            TapAndGoTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: false) {
                TapAndGoApp(pendingTaskToken = pendingToken.value, onTaskTokenConsumed = { pendingToken.value = null })
            }
        }
    }
}
