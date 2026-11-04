package dev.nfcstudio.app.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.data.export.BackupService
import dev.nfcstudio.app.data.repository.HistoryRepository
import dev.nfcstudio.app.data.settings.AppSettings
import dev.nfcstudio.app.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val backup: BackupService,
) : ViewModel() {

    val state: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<BackupService.Outcome?>(null)
    val message: StateFlow<BackupService.Outcome?> = _message.asStateFlow()

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            settings.update(transform)
            history.prune()
        }
    }

    fun exportAll(target: Uri) {
        viewModelScope.launch { _message.value = backup.export(target, BackupService.Section.entries.toSet()) }
    }

    fun import(source: Uri) {
        viewModelScope.launch { _message.value = backup.import(source) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
