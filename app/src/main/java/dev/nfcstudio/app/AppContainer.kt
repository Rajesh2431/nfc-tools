package dev.nfcstudio.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.nfcstudio.app.core.nfc.PlatformTagGateway
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.data.database.AppDatabase
import dev.nfcstudio.app.data.export.BackupService
import dev.nfcstudio.app.data.repository.HistoryRepository
import dev.nfcstudio.app.data.repository.TemplateRepository
import dev.nfcstudio.app.data.settings.SettingsRepository
import dev.nfcstudio.app.feature.history.HistoryViewModel
import dev.nfcstudio.app.feature.home.HomeViewModel
import dev.nfcstudio.app.feature.scanner.ScanViewModel
import dev.nfcstudio.app.feature.settings.SettingsViewModel
import dev.nfcstudio.app.feature.writer.TemplatesViewModel
import dev.nfcstudio.app.feature.writer.WriteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Manual dependency wiring (spec § 5: Hilt only if complexity warrants it).
 * One instance per process, created in [NfcStudioApplication].
 */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val tagGateway: TagGateway = PlatformTagGateway
    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings = SettingsRepository(appContext)
    val history by lazy { HistoryRepository(database.scanDao(), settings) }
    val templates by lazy { TemplateRepository(database.templateDao()) }
    val backup by lazy { BackupService(database, settings, appContext.contentResolver) }

    /** Applies the retention policy once per process start. */
    fun onStart() {
        appScope.launch { history.prune() }
    }

    fun scanViewModel() = ScanViewModel(gateway = tagGateway, onScanned = { history.record(it) })
    fun writeViewModel() = WriteViewModel(gateway = tagGateway)
    fun templatesViewModel() = TemplatesViewModel(templates)
    fun historyViewModel() = HistoryViewModel(history, backup)
    fun settingsViewModel() = SettingsViewModel(settings, history, backup)
    fun homeViewModel() = HomeViewModel(history, templates)
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NfcStudioApplication).container
