package dev.nfcstudio.app.feature.advanced

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.core.apdu.ApduSafety
import dev.nfcstudio.app.core.apdu.CommandApdu
import dev.nfcstudio.app.core.apdu.ResponseApdu
import dev.nfcstudio.app.core.common.toHex
import dev.nfcstudio.app.core.nfc.ApduChannel
import dev.nfcstudio.app.core.nfc.IsoDepSession
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Advanced-mode ISO-DEP console (spec 4.6). Commands are validated before
 * transmission; state-changing commands are refused unless the user enabled
 * them in Settings. The channel is closed when the console is left.
 */
class ApduConsoleViewModel(
    private val channel: ApduChannel?,
    private val allowStateChanging: Boolean,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val clock: () -> Long = System::nanoTime,
) : ViewModel() {

    sealed interface Entry {
        val commandHex: String

        data class Response(override val commandHex: String, val response: ResponseApdu, val millis: Long) : Entry
        data class Failure(override val commandHex: String, val error: NfcError) : Entry
        data class NoStatusWord(override val commandHex: String, val rawHex: String) : Entry
    }

    data class State(
        val input: String = "",
        val timeoutMs: Int = 1_000,
        val busy: Boolean = false,
        val problem: CommandApdu.Problem? = null,
        val blocked: Boolean = false,
        val transcript: List<Entry> = emptyList(),
    )

    val available: Boolean get() = channel != null
    val stateChangingAllowed: Boolean get() = allowStateChanging

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setInput(value: String) = _state.update { it.copy(input = value, problem = null, blocked = false) }

    fun setTimeout(ms: Int) = _state.update { it.copy(timeoutMs = ms.coerceIn(IsoDepSession.MIN_TIMEOUT, IsoDepSession.MAX_TIMEOUT)) }

    fun send() {
        val ch = channel ?: return
        val s = _state.value
        if (s.busy) return
        val apdu = when (val p = CommandApdu.parse(s.input, ch.maxTransceiveLength, ch.extendedLengthSupported)) {
            is CommandApdu.Parse.Invalid -> return _state.update { it.copy(problem = p.problem) }
            is CommandApdu.Parse.Ok -> p.apdu
        }
        if (ApduSafety.isStateChanging(apdu) && !allowStateChanging) {
            _state.update { it.copy(blocked = true) }
            return
        }
        _state.update { it.copy(busy = true) }
        val hex = apdu.bytes.toHex().uppercase()
        viewModelScope.launch {
            val start = clock()
            val result = withContext(io) { ch.transceive(apdu.bytes, s.timeoutMs) }
            val elapsed = (clock() - start) / 1_000_000
            val entry = when (result) {
                is NfcResult.Err -> Entry.Failure(hex, result.error)
                is NfcResult.Ok -> ResponseApdu.parse(result.value)?.let { Entry.Response(hex, it, elapsed) }
                    ?: Entry.NoStatusWord(hex, result.value.toHex().uppercase())
            }
            _state.update { it.copy(busy = false, transcript = listOf(entry) + it.transcript.take(99)) }
        }
    }

    fun clearTranscript() = _state.update { it.copy(transcript = emptyList()) }

    override fun onCleared() {
        channel?.close()
    }
}
