package dev.nfcstudio.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.nfcstudio.app.core.nfc.PlatformTagGateway
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.feature.scanner.ScanViewModel
import dev.nfcstudio.app.feature.writer.WriteViewModel

/**
 * Manual dependency wiring (spec § 5: Hilt only if complexity warrants it).
 * One instance per process, created in [NfcStudioApplication].
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val tagGateway: TagGateway = PlatformTagGateway

    fun scanViewModel() = ScanViewModel(gateway = tagGateway)

    fun writeViewModel() = WriteViewModel(gateway = tagGateway)
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NfcStudioApplication).container
