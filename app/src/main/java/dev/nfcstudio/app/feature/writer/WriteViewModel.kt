package dev.nfcstudio.app.feature.writer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.nfcstudio.app.core.ndef.NdefSizeEstimator
import dev.nfcstudio.app.core.ndef.RecordProblem
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.RecordValidator
import dev.nfcstudio.app.core.nfc.ContentSummary
import dev.nfcstudio.app.core.nfc.DiscoveredTag
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagGateway
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.core.nfc.Verification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Editor + tag operation state machine for the Write screen.
 *
 * Editing → AwaitingTag → (confirmation) → Working → Success | Failed.
 * Every destructive step — overwriting existing content, formatting a blank
 * tag, clearing a tag, permanently locking it — needs an explicit confirmation
 * (AGENTS rule 7). Unknown or unreadable content is treated as "may contain
 * data", never as empty.
 */
class WriteViewModel(
    private val gateway: TagGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    enum class DraftKind { TEXT, URI, MIME }

    /** What the armed tag session will do. */
    enum class Operation { WRITE, ERASE, LOCK }

    sealed interface Phase {
        data object Editing : Phase

        /** Shown before arming a permanent lock; nothing is armed yet. */
        data object ConfirmLockIntent : Phase
        data class AwaitingTag(val operation: Operation) : Phase
        data class ConfirmOverwrite(val existing: ContentSummary) : Phase
        data object ConfirmFormat : Phase
        data class ConfirmErase(val existing: ContentSummary) : Phase
        data class ConfirmLock(val existing: ContentSummary) : Phase
        data class Working(val operation: Operation) : Phase
        data class Success(val operation: Operation, val verification: Verification?, val bytes: Int) : Phase
        data class Failed(val error: NfcError) : Phase
    }

    data class Draft(
        val kind: DraftKind = DraftKind.TEXT,
        val text: String = "",
        val language: String = "en",
        val uri: String = "",
        val mimeType: String = "text/plain",
        val mimePayload: String = "",
    ) {
        fun toSpec(): RecordSpec = when (kind) {
            DraftKind.TEXT -> RecordSpec.Text(text = text, language = language.trim())
            DraftKind.URI -> RecordSpec.Uri(uri = uri.trim())
            DraftKind.MIME -> RecordSpec.Mime(mimeType = mimeType.trim().lowercase(), payload = mimePayload)
        }
    }

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft.asStateFlow()

    private val _records = MutableStateFlow<List<RecordSpec>>(emptyList())
    val records: StateFlow<List<RecordSpec>> = _records.asStateFlow()

    /** Exact encoded size; records are validated on entry so encoding cannot throw. */
    val estimatedSize: StateFlow<Int> = _records
        .map { NdefSizeEstimator.messageSize(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _phase = MutableStateFlow<Phase>(Phase.Editing)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _draftError = MutableStateFlow<RecordProblem?>(null)
    val draftError: StateFlow<RecordProblem?> = _draftError.asStateFlow()

    private var pendingTag: DiscoveredTag? = null
    private val busy = AtomicBoolean(false)

    fun updateDraft(transform: (Draft) -> Draft) {
        _draft.value = transform(_draft.value)
        _draftError.value = null
    }

    /** Validates the draft and appends it. Invalid drafts never reach the encoder. */
    fun addDraftRecord(): Boolean {
        val spec = _draft.value.toSpec()
        val problem = RecordValidator.validate(spec)
        if (problem != null) {
            _draftError.value = problem
            return false
        }
        _records.value += spec
        _draft.value = Draft(kind = _draft.value.kind, language = _draft.value.language)
        _draftError.value = null
        return true
    }

    fun removeRecord(index: Int) {
        _records.value = _records.value.filterIndexed { i, _ -> i != index }
    }

    fun moveRecord(index: Int, delta: Int) {
        val list = _records.value.toMutableList()
        val target = index + delta
        if (index !in list.indices || target !in list.indices) return
        list.add(target, list.removeAt(index))
        _records.value = list
    }

    /** Replaces the message, e.g. from a template; invalid records are dropped. */
    fun replaceRecords(specs: List<RecordSpec>): Int {
        val valid = specs.filter { it != RecordSpec.Empty && RecordValidator.validate(it) == null }
        _records.value = valid
        return specs.size - valid.size
    }

    fun startWrite() {
        if (_records.value.isEmpty() || _phase.value != Phase.Editing) return
        arm(Operation.WRITE)
    }

    fun startErase() {
        if (_phase.value != Phase.Editing) return
        arm(Operation.ERASE)
    }

    /** Locking needs a first confirmation before the reader is even armed. */
    fun requestLock() {
        if (_phase.value == Phase.Editing) _phase.value = Phase.ConfirmLockIntent
    }

    fun confirmLockIntent() {
        if (_phase.value == Phase.ConfirmLockIntent) arm(Operation.LOCK)
    }

    private fun arm(operation: Operation) {
        pendingTag = null
        _phase.value = Phase.AwaitingTag(operation)
    }

    fun cancel() {
        pendingTag = null
        _phase.value = Phase.Editing
    }

    /** Declining a tag-specific confirmation returns to waiting for another tag. */
    fun declineConfirmation() {
        val operation = when (val p = _phase.value) {
            is Phase.ConfirmOverwrite, Phase.ConfirmFormat -> Operation.WRITE
            is Phase.ConfirmErase -> Operation.ERASE
            is Phase.ConfirmLock -> Operation.LOCK
            Phase.ConfirmLockIntent -> return cancel()
            else -> return
        }
        arm(operation)
    }

    fun dismissResult() {
        if (_phase.value is Phase.Success || _phase.value is Phase.Failed) _phase.value = Phase.Editing
    }

    fun confirm() {
        val tag = pendingTag ?: return
        when (_phase.value) {
            is Phase.ConfirmOverwrite -> execute(Operation.WRITE, tag) { gateway.write(it, _records.value) }
            Phase.ConfirmFormat -> execute(Operation.WRITE, tag) { gateway.formatAndWrite(it, _records.value) }
            is Phase.ConfirmErase -> execute(Operation.ERASE, tag) { gateway.write(it, listOf(RecordSpec.Empty)) }
            is Phase.ConfirmLock -> execute(Operation.LOCK, tag) { t ->
                when (val r = gateway.makeReadOnly(t)) {
                    is NfcResult.Ok -> NfcResult.Ok(null)
                    is NfcResult.Err -> r
                }
            }

            else -> Unit
        }
    }

    /** Called from the reader-mode callback (binder thread). */
    fun onTag(tag: DiscoveredTag) {
        val phase = _phase.value as? Phase.AwaitingTag ?: return
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                when (val r = withContext(io) { gateway.read(tag) }) {
                    is NfcResult.Err -> _phase.value = Phase.Failed(r.error)
                    is NfcResult.Ok -> route(phase.operation, tag, r.value)
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun route(operation: Operation, tag: DiscoveredTag, snapshot: TagSnapshot) {
        val ndef = snapshot.ndef
        when (operation) {
            Operation.WRITE -> when {
                ndef != null -> {
                    val needed = NdefSizeEstimator.messageSize(_records.value)
                    val capacity = ndef.maxCapacityBytes
                    when {
                        ndef.isWritable == false -> fail(NfcError.ReadOnly)
                        capacity != null && needed > capacity -> fail(NfcError.TooLarge(needed, capacity))
                        ndef.content == NdefContent.Empty ->
                            runOperation(Operation.WRITE, tag) { gateway.write(it, _records.value) }

                        else -> askFor(tag, Phase.ConfirmOverwrite(ContentSummary.of(ndef.content)))
                    }
                }

                snapshot.formatable -> askFor(tag, Phase.ConfirmFormat)
                else -> fail(NfcError.NoNdefSupport)
            }

            Operation.ERASE -> when {
                ndef == null -> fail(NfcError.NoNdefSupport)
                ndef.isWritable == false -> fail(NfcError.ReadOnly)
                else -> askFor(tag, Phase.ConfirmErase(ContentSummary.of(ndef.content)))
            }

            Operation.LOCK -> when {
                ndef == null -> fail(NfcError.NoNdefSupport)
                ndef.isWritable == false -> fail(NfcError.ReadOnly)
                ndef.canMakeReadOnly == false -> fail(NfcError.LockUnsupported)
                else -> askFor(tag, Phase.ConfirmLock(ContentSummary.of(ndef.content)))
            }
        }
    }

    private fun askFor(tag: DiscoveredTag, confirmation: Phase) {
        pendingTag = tag
        _phase.value = confirmation
    }

    private fun fail(error: NfcError) {
        _phase.value = Phase.Failed(error)
    }

    private fun execute(
        operation: Operation,
        tag: DiscoveredTag,
        block: (DiscoveredTag) -> NfcResult<Verification?>,
    ) {
        if (!busy.compareAndSet(false, true)) return
        pendingTag = null
        viewModelScope.launch {
            try {
                runOperation(operation, tag, block)
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun runOperation(
        operation: Operation,
        tag: DiscoveredTag,
        block: (DiscoveredTag) -> NfcResult<Verification?>,
    ) {
        _phase.value = Phase.Working(operation)
        val bytes = when (operation) {
            Operation.WRITE -> estimatedSizeNow()
            Operation.ERASE -> NdefSizeEstimator.messageSize(listOf(RecordSpec.Empty))
            Operation.LOCK -> 0
        }
        _phase.value = when (val r = withContext(io) { block(tag) }) {
            is NfcResult.Ok -> Phase.Success(operation, r.value, bytes)
            is NfcResult.Err -> Phase.Failed(r.error)
        }
    }

    private fun estimatedSizeNow(): Int = NdefSizeEstimator.messageSize(_records.value)
}
