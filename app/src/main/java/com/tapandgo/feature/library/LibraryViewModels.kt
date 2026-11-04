package com.tapandgo.feature.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tapandgo.core.ndef.DecodedRecord
import com.tapandgo.data.database.SavedTagEntity
import com.tapandgo.data.library.Difference
import com.tapandgo.data.library.Library
import com.tapandgo.data.library.LibraryFilter
import com.tapandgo.data.library.LibraryMeta
import com.tapandgo.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class TextExport { CSV, TXT }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val repo: LibraryRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _filter = MutableStateFlow(LibraryFilter())
    val filter: StateFlow<LibraryFilter> = _filter.asStateFlow()
    val tags: StateFlow<List<SavedTagEntity>?> = _filter.flatMapLatest { repo.observe(it) }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val folders: StateFlow<List<String>> = repo.observeFolders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _exported = MutableStateFlow<Boolean?>(null)
    val exported: StateFlow<Boolean?> = _exported.asStateFlow()

    fun update(t: (LibraryFilter) -> LibraryFilter) = _filter.update(t)

    fun toggleFavourite(e: SavedTagEntity) = viewModelScope.launch { repo.setFavourite(e.id, !e.favourite) }

    fun export(target: Uri, format: TextExport) {
        viewModelScope.launch {
            val tags = repo.all()
            val text = if (format == TextExport.CSV) Library.toCsv(tags) else Library.toText(tags)
            _exported.value = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(target, "wt")!!.use { it.write(text.toByteArray()) } }.isSuccess
            }
        }
    }
}

@HiltViewModel
class LibraryDetailViewModel @Inject constructor(
    private val repo: LibraryRepository,
    handle: SavedStateHandle,
) : ViewModel() {
    val id: Long = handle.get<Long>("id") ?: 0L
    val tag: StateFlow<SavedTagEntity?> = repo.observe(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val others: StateFlow<List<SavedTagEntity>> = repo.observe(LibraryFilter()).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _problem = MutableStateFlow<LibraryMeta.Problem?>(null)
    val problem: StateFlow<LibraryMeta.Problem?> = _problem.asStateFlow()
    private val _differences = MutableStateFlow<Pair<SavedTagEntity, List<Difference>>?>(null)
    val differences: StateFlow<Pair<SavedTagEntity, List<Difference>>?> = _differences.asStateFlow()
    private val _photoFailed = MutableStateFlow(false)
    val photoFailed: StateFlow<Boolean> = _photoFailed.asStateFlow()

    fun records(e: SavedTagEntity): List<DecodedRecord> = Library.records(e)
    fun photo(e: SavedTagEntity) = repo.photoFile(e)

    fun save(meta: LibraryMeta, onSaved: () -> Unit) {
        meta.validate()?.let { _problem.value = it; return }
        viewModelScope.launch { if (repo.updateMeta(id, meta)) { _problem.value = null; onSaved() } }
    }

    fun toggleFavourite() = viewModelScope.launch { tag.value?.let { repo.setFavourite(id, !it.favourite) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(id); onDone() }
    fun attachPhoto(uri: Uri) = viewModelScope.launch { _photoFailed.value = !repo.attachPhoto(id, uri) }

    fun compareWith(other: SavedTagEntity) {
        val me = tag.value ?: return
        _differences.value = other to Library.compare(me, other)
    }

    fun clearComparison() { _differences.value = null }
}
