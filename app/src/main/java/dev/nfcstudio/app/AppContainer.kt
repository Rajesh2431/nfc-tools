package dev.nfcstudio.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.nfcstudio.app.core.nfc.PlatformTagGateway
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.data.database.AppDatabase
import dev.nfcstudio.app.data.export.BackupService
import dev.nfcstudio.app.data.repository.HistoryRepository
import dev.nfcstudio.app.data.repository.TaskRepository
import dev.nfcstudio.app.feature.tasks.IntentActionPerformer
import dev.nfcstudio.app.feature.tasks.ProfileEditorViewModel
import dev.nfcstudio.app.feature.tasks.TaskRunViewModel
import dev.nfcstudio.app.feature.tasks.TasksViewModel
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
    val tasks by lazy { TaskRepository(database.taskDao()) }

    /** Uses the Activity context so started activities join the app's task. */
    fun actionPerformer(context: Context) = IntentActionPerformer(context)

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
    fun tasksViewModel() = TasksViewModel(tasks)
    fun profileEditorViewModel(id: Long) = ProfileEditorViewModel(tasks, id)
    fun taskRunViewModel(id: Long, context: Context) = TaskRunViewModel(tasks, actionPerformer(context), id)
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NfcStudioApplication).container
