package com.tapandgo.feature.scanner

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.core.nfc.NfcState
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.data.repository.StoredProfile
import com.tapandgo.data.repository.TaskRepository
import com.tapandgo.data.settings.AppSettings
import com.tapandgo.data.settings.SettingsRepository
import com.tapandgo.feature.advanced.DiagnosticsExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Read-tab context that is not tag I/O: matching profiles, settings, diagnostics export. */
@HiltViewModel
class ReadExtrasViewModel @Inject constructor(
    tasks: TaskRepository,
    settings: SettingsRepository,
    private val exporter: DiagnosticsExporter,
    private val library: com.tapandgo.data.library.LibraryRepository,
) : ViewModel() {

    sealed interface SaveState {
        data class Duplicates(val names: List<String>) : SaveState
        data class Saved(val id: Long) : SaveState
        data class Invalid(val problem: com.tapandgo.data.library.LibraryMeta.Problem) : SaveState
    }

    private val _save = MutableStateFlow<SaveState?>(null)
    val saveState: StateFlow<SaveState?> = _save.asStateFlow()

    /** Saves to the library; warns once about duplicates unless [force]. */
    fun saveToLibrary(snapshot: TagSnapshot, meta: com.tapandgo.data.library.LibraryMeta, force: Boolean) {
        meta.validate()?.let { _save.value = SaveState.Invalid(it); return }
        viewModelScope.launch {
            if (!force) {
                val dups = library.duplicatesOf(snapshot, meta)
                if (dups.isNotEmpty()) { _save.value = SaveState.Duplicates(dups.map { it.name }); return@launch }
            }
            library.save(snapshot, meta)?.let { _save.value = SaveState.Saved(it) }
        }
    }

    fun clearSave() { _save.value = null }
    val profiles: StateFlow<List<StoredProfile>> = tasks.observeProfiles().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val settings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _diagnosticsSaved = MutableStateFlow<Boolean?>(null)
    val diagnosticsSaved: StateFlow<Boolean?> = _diagnosticsSaved.asStateFlow()

    fun exportDiagnostics(target: Uri, snapshot: TagSnapshot, nfcState: NfcState) {
        viewModelScope.launch { _diagnosticsSaved.value = exporter.export(target, snapshot, nfcState) }
    }
}
