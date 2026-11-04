package com.tapandgo.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.tapandgo.data.export.BackupService
import com.tapandgo.data.repository.HistoryRepository
import com.tapandgo.data.settings.AppSettings
import com.tapandgo.data.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    /** @param password null for a plain JSON backup, else an encrypted envelope. */
    fun exportAll(target: Uri, password: CharArray?) {
        viewModelScope.launch {
            _message.value = backup.export(target, BackupService.Section.entries.toSet(), password)
            password?.fill('\u0000')
        }
    }

    private var pendingImport: Uri? = null

    fun import(source: Uri, password: CharArray? = null) {
        viewModelScope.launch {
            val r = backup.import(source, password)
            password?.fill('\u0000')
            pendingImport = if (r == BackupService.Outcome.NeedsPassword || r == BackupService.Outcome.WrongPassword) source else null
            _message.value = r
        }
    }

    /** Retries the last encrypted import with a password. */
    fun importWithPassword(password: CharArray) {
        pendingImport?.let { import(it, password) }
    }

    fun clearHistory() {
        viewModelScope.launch { history.clear() }
    }

    fun dismissMessage() {
        _message.value = null
    }
}
