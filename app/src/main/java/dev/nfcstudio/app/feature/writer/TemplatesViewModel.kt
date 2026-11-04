package dev.nfcstudio.app.feature.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.data.repository.Template
import dev.nfcstudio.app.data.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Template library for the Write screen (spec 4.5: stored independently from history). */
class TemplatesViewModel(private val repository: TemplateRepository) : ViewModel() {

    val templates: StateFlow<List<Template>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saveProblem = MutableStateFlow<TemplateRepository.Problem?>(null)
    val saveProblem: StateFlow<TemplateRepository.Problem?> = _saveProblem.asStateFlow()

    /** Validates synchronously so the dialog can stay open on error. */
    fun save(name: String, description: String, records: List<RecordSpec>, onSaved: () -> Unit) {
        TemplateRepository.validate(name, description, records)?.let {
            _saveProblem.value = it
            return
        }
        viewModelScope.launch {
            _saveProblem.value = repository.save(name, description, records)
            if (_saveProblem.value == null) onSaved()
        }
    }

    fun clearProblem() {
        _saveProblem.value = null
    }

    fun duplicate(id: Long, suffix: String) {
        viewModelScope.launch { repository.duplicate(id, suffix) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
