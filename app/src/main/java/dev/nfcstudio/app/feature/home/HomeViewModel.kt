package dev.nfcstudio.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.data.database.ScanEntryEntity
import dev.nfcstudio.app.data.repository.HistoryRepository
import dev.nfcstudio.app.data.repository.TemplateRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(history: HistoryRepository, templates: TemplateRepository) : ViewModel() {
    val recent: StateFlow<List<ScanEntryEntity>?> = history.observeRecent(3)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val templateCount: StateFlow<Int> = templates.observeAll().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
