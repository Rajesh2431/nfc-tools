package com.tapandgo.feature.history

import android.net.Uri
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.DecodedRecord
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.data.database.ScanEntryEntity
import com.tapandgo.data.export.BackupService
import com.tapandgo.data.repository.HistoryFilter
import com.tapandgo.data.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: HistoryRepository,
    private val backup: BackupService,
) : ViewModel() {

    private val _filter = MutableStateFlow(HistoryFilter())
    val filter: StateFlow<HistoryFilter> = _filter.asStateFlow()

    val entries: StateFlow<List<ScanEntryEntity>?> = _filter
        .flatMapLatest { repository.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val technologies: StateFlow<List<String>> = repository.observeTechnologies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<BackupService.Outcome?>(null)
    val message: StateFlow<BackupService.Outcome?> = _message.asStateFlow()

    fun updateFilter(transform: (HistoryFilter) -> HistoryFilter) = _filter.update(transform)

    fun toggleFavourite(entry: ScanEntryEntity) {
        viewModelScope.launch { repository.setFavourite(entry.id, !entry.favourite) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.clear() }
    }

    fun export(target: Uri) {
        viewModelScope.launch { _message.value = backup.export(target, setOf(BackupService.Section.HISTORY)) }
    }

    fun dismissMessage() {
        _message.value = null
    }

    companion object {
        /** Re-decodes a stored payload; null when none was stored or it no longer parses. */
        fun decodePayload(payloadHex: String?): List<DecodedRecord>? {
            val bytes = payloadHex?.hexToBytesOrNull() ?: return null
            return runCatching { NdefParser.decodeAll(NdefMessageCodec.parse(bytes)) }.getOrNull()
        }
    }
}
