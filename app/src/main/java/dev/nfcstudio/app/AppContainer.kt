package dev.nfcstudio.app

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import dev.nfcstudio.app.core.nfc.CurrentTagStore
import dev.nfcstudio.app.core.nfc.IsoDepSession
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.PlatformTagGateway
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.feature.advanced.ApduConsoleViewModel
import dev.nfcstudio.app.feature.advanced.DiagnosticsBuilder
import kotlinx.coroutines.withContext
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
    val tagStore = CurrentTagStore()

    /** Records another screen wants the Write tab to load (e.g. a task tag). */
    val pendingWrite = kotlinx.coroutines.flow.MutableStateFlow<List<dev.nfcstudio.app.core.ndef.RecordSpec>?>(null)

    /** Uses the Activity context so started activities join the app's task. */
    fun actionPerformer(context: Context) = IntentActionPerformer(context)

    /** Applies the retention policy once per process start. */
    fun onStart() {
        appScope.launch { history.prune() }
    }

    fun scanViewModel() = ScanViewModel(gateway = tagGateway, onScanned = { history.record(it) }, onTagRetained = tagStore::set)
    fun writeViewModel() = WriteViewModel(gateway = tagGateway)
    fun templatesViewModel() = TemplatesViewModel(templates)
    fun historyViewModel() = HistoryViewModel(history, backup)
    fun settingsViewModel() = SettingsViewModel(settings, history, backup)
    fun otherViewModel() = dev.nfcstudio.app.feature.other.OtherViewModel(tagGateway)
    fun tasksViewModel() = TasksViewModel(tasks)
    fun profileEditorViewModel(id: Long) = ProfileEditorViewModel(tasks, id)
    /** Opens an ISO-DEP channel only for an IsoDep tag that is still the current one. */
    fun apduConsoleViewModel(allowStateChanging: Boolean) = ApduConsoleViewModel(
        channel = tagStore.current.value?.takeIf { it.snapshot.supportsIsoDep }?.let { IsoDepSession(it.tag) },
        allowStateChanging = allowStateChanging,
    )

    /** Writes payload-free diagnostics to a user-chosen document. */
    suspend fun exportDiagnostics(target: android.net.Uri, snapshot: TagSnapshot, nfcState: NfcState): Boolean =
        withContext(Dispatchers.IO) {
            val d = DiagnosticsBuilder.build(
                snapshot, nfcState, BuildConfig.VERSION_NAME, android.os.Build.MANUFACTURER, android.os.Build.MODEL,
                android.os.Build.VERSION.SDK_INT, history.recentResultCodes(),
            )
            runCatching {
                appContext.contentResolver.openOutputStream(target, "wt")!!.use { it.write(DiagnosticsBuilder.encode(d).toByteArray()) }
            }.isSuccess
        }

    fun taskRunViewModel(id: Long, autoStarted: Boolean, context: Context) = TaskRunViewModel(tasks, actionPerformer(context), id, autoStarted)
}

@Composable
fun appContainer(): AppContainer =
    (LocalContext.current.applicationContext as NfcStudioApplication).container
