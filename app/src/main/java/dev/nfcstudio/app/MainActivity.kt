package dev.nfcstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.nfcstudio.app.data.settings.ThemeMode
import dev.nfcstudio.app.navigation.NfcStudioApp
import dev.nfcstudio.app.ui.theme.NfcStudioTheme

/** Single-activity host for the Compose UI; theme follows Settings. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settingsFlow = (application as NfcStudioApplication).container.settings.settings
        setContent {
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null)
            val dark = when (settings?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            NfcStudioTheme(darkTheme = dark, dynamicColor = settings?.dynamicColor ?: true) {
                NfcStudioApp()
            }
        }
    }
}
