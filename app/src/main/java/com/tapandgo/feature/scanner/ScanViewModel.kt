package com.tapandgo.feature.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.tapandgo.core.nfc.CurrentTagStore
import com.tapandgo.core.nfc.DiscoveredTag
import com.tapandgo.core.nfc.NfcError
import com.tapandgo.core.nfc.NfcResult
import com.tapandgo.core.nfc.TagGateway
import com.tapandgo.core.nfc.TagSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Scan screen state. Tag I/O runs on [io]; every outcome is a real snapshot
 * or a typed error. [onScanned] lets the persistence layer record results.
 */
@HiltViewModel
class ScanViewModel(
    private val gateway: TagGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onScanned: suspend (NfcResult<TagSnapshot>) -> Unit = {},
    private val onTagRetained: (CurrentTagStore.Current?) -> Unit = {},
) : ViewModel() {

    @Inject constructor(
        gateway: TagGateway,
        history: com.tapandgo.data.repository.HistoryRepository,
        store: CurrentTagStore,
        @com.tapandgo.di.IoDispatcher io: CoroutineDispatcher,
    ) : this(gateway, io, { history.record(it) }, store::set)

    data class State(
        val working: Boolean = false,
        val snapshot: TagSnapshot? = null,
        val error: NfcError? = null,
        /** Increments per completed scan so the UI can reset per-scan state. */
        val scanCount: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val busy = AtomicBoolean(false)

    /** Called from the reader-mode callback (binder thread). Ignored while busy. */
    fun onTag(tag: DiscoveredTag) {
        if (!busy.compareAndSet(false, true)) return
        _state.update { it.copy(working = true) }
        viewModelScope.launch {
            try {
                val result = withContext(io) { gateway.read(tag, identifyChip = true) }
                _state.update { s ->
                    when (result) {
                        is NfcResult.Ok -> State(snapshot = result.value, scanCount = s.scanCount + 1)
                        is NfcResult.Err -> State(error = result.error, scanCount = s.scanCount + 1)
                    }
                }
                onTagRetained((result as? NfcResult.Ok)?.let { CurrentTagStore.Current(tag, it.value) })
                onScanned(result)
            } finally {
                busy.set(false)
            }
        }
    }

    /** Clears the current result/error so the next tag starts fresh. */
    fun clear() {
        onTagRetained(null)
        _state.update { State(scanCount = it.scanCount) }
    }
}
