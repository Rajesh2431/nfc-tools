package dev.nfcstudio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.nfcstudio.app.navigation.NfcStudioApp
import dev.nfcstudio.app.ui.theme.NfcStudioTheme

/**
 * Single-activity host for the Compose UI.
 * All navigation happens inside [NfcStudioApp]; no fragments.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NfcStudioTheme {
                NfcStudioApp()
            }
        }
    }
}
