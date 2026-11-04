package dev.nfcstudio.app.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.core.nfc.CurrentTagStore
import dev.nfcstudio.app.core.nfc.DiscoveredTag
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.core.nfc.TagSnapshot
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
class ScanViewModel(
    private val gateway: TagGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val onScanned: suspend (NfcResult<TagSnapshot>) -> Unit = {},
    private val onTagRetained: (CurrentTagStore.Current?) -> Unit = {},
) : ViewModel() {

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
                val result = withContext(io) { gateway.read(tag) }
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
