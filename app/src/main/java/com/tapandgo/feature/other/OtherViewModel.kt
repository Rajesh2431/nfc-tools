package com.tapandgo.feature.other

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.nfc.ChipParsing
import com.tapandgo.core.nfc.ContentSummary
import com.tapandgo.core.nfc.DiscoveredTag
import com.tapandgo.core.nfc.MemoryDump
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.core.nfc.NfcError
import com.tapandgo.core.nfc.NfcResult
import com.tapandgo.core.nfc.TagGateway
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.core.nfc.Verification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tag maintenance operations (copy, erase, lock, format, password, memory).
 * Every destructive step needs an explicit confirmation after the tag's
 * current content is shown (AGENTS rule 7); unknown content counts as data.
 */
@HiltViewModel
class OtherViewModel(
    private val gateway: TagGateway,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val loadSaved: suspend (Long) -> ByteArray? = { null },
) : ViewModel() {

    @Inject constructor(gateway: TagGateway, @com.tapandgo.di.IoDispatcher io: CoroutineDispatcher, library: com.tapandgo.data.library.LibraryRepository) :
        this(gateway, io, { id -> library.byId(id)?.payloadHex?.hexToBytesOrNull() })

    /**
     * Restores a saved library message: skips the source step of Copy and
     * goes straight to "hold the target tag" with the usual checks,
     * overwrite confirmation and read-back verification.
     */
    fun startRestore(savedId: Long) {
        if (_phase.value !is Phase.Menu) return
        viewModelScope.launch {
            val bytes = loadSaved(savedId)
            if (bytes == null || bytes.isEmpty()) {
                _phase.value = Phase.Failed(Op.COPY, OtherError.NothingToCopy)
                return@launch
            }
            reset()
            copySource = bytes
            _phase.value = Phase.Awaiting(Op.COPY, copyTarget = true, copiedBytes = bytes.size)
        }
    }

    enum class Op { COPY, ERASE, LOCK, FORMAT, SET_PASSWORD, REMOVE_PASSWORD, READ_MEMORY }

    sealed interface Phase {
        data object Menu : Phase

        /** Pre-arming step: lock intent or password entry. */
        data class Prepare(val op: Op) : Phase
        data class Awaiting(val op: Op, val copyTarget: Boolean = false, val copiedBytes: Int = 0) : Phase
        data class Confirm(val op: Op, val existing: ContentSummary?) : Phase
        data class Working(val op: Op) : Phase
        data class Done(val op: Op, val verification: Verification? = null, val dump: MemoryDump? = null) : Phase
        data class Failed(val op: Op, val error: OtherError) : Phase
    }

    /** Typed failures: NFC errors plus operation-specific outcomes. */
    sealed interface OtherError {
        data class Nfc(val error: NfcError) : OtherError
        data object NothingToCopy : OtherError
        data object SameTag : OtherError
        data object AlreadyFormatted : OtherError
        data object PasswordMismatch : OtherError
        data object PasswordLength : OtherError
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Menu)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private var pendingTag: DiscoveredTag? = null
    private var copySource: ByteArray? = null
    private var copySourceUid: String? = null
    private var password: String = ""
    private var protectReads = false
    private val busy = AtomicBoolean(false)

    fun start(op: Op) {
        if (_phase.value !is Phase.Menu) return
        reset()
        _phase.value = when (op) {
            Op.LOCK, Op.SET_PASSWORD, Op.REMOVE_PASSWORD -> Phase.Prepare(op)
            else -> Phase.Awaiting(op)
        }
    }

    fun confirmLockIntent() {
        if (_phase.value == Phase.Prepare(Op.LOCK)) _phase.value = Phase.Awaiting(Op.LOCK)
    }

    /** Validates password entry and arms the reader. */
    fun submitPassword(value: String, repeat: String?, readProtection: Boolean) {
        val p = _phase.value as? Phase.Prepare ?: return
        if (p.op != Op.SET_PASSWORD && p.op != Op.REMOVE_PASSWORD) return
        if (value.length !in ChipParsing.MIN_PASSWORD..ChipParsing.MAX_PASSWORD) return fail(p.op, OtherError.PasswordLength, keepPrepare = true)
        if (p.op == Op.SET_PASSWORD && value != repeat) return fail(p.op, OtherError.PasswordMismatch, keepPrepare = true)
        password = value
        protectReads = readProtection
        _prepareError.value = null
        _phase.value = Phase.Awaiting(p.op)
    }

    private val _prepareError = MutableStateFlow<OtherError?>(null)
    val prepareError: StateFlow<OtherError?> = _prepareError.asStateFlow()

    fun cancel() {
        reset()
        _phase.value = Phase.Menu
    }

    fun decline() {
        val p = _phase.value as? Phase.Confirm ?: return
        pendingTag = null
        _phase.value = Phase.Awaiting(p.op, copyTarget = p.op == Op.COPY, copiedBytes = copySource?.size ?: 0)
    }

    private fun reset() {
        pendingTag = null; copySource = null; copySourceUid = null; password = ""; protectReads = false
        _prepareError.value = null
    }

    /** Reader-mode callback (binder thread). */
    fun onTag(tag: DiscoveredTag) {
        val awaiting = _phase.value as? Phase.Awaiting ?: return
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                handle(awaiting, tag)
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun handle(a: Phase.Awaiting, tag: DiscoveredTag) {
        val op = a.op
        if (op == Op.READ_MEMORY) {
            _phase.value = Phase.Working(op)
            _phase.value = when (val r = withContext(io) { gateway.dumpMemory(tag) }) {
                is NfcResult.Ok -> Phase.Done(op, dump = r.value)
                is NfcResult.Err -> Phase.Failed(op, OtherError.Nfc(r.error))
            }
            return
        }
        if (op == Op.REMOVE_PASSWORD) return run(op, tag) { gateway.removePassword(it, password).map { null } }

        val snapshot = when (val r = withContext(io) { gateway.read(tag, identifyChip = op == Op.SET_PASSWORD) }) {
            is NfcResult.Err -> return fail(op, OtherError.Nfc(r.error))
            is NfcResult.Ok -> r.value
        }
        val ndef = snapshot.ndef
        when (op) {
            Op.COPY -> if (!a.copyTarget) {
                val records = ndef?.content as? NdefContent.Records ?: return fail(op, OtherError.NothingToCopy)
                copySource = records.rawMessageHex.hexToBytesOrNull()
                copySourceUid = snapshot.uidHex
                _phase.value = Phase.Awaiting(op, copyTarget = true, copiedBytes = records.sizeBytes)
            } else {
                val source = copySource ?: return fail(op, OtherError.NothingToCopy)
                if (snapshot.uidHex != null && snapshot.uidHex == copySourceUid) return fail(op, OtherError.SameTag, keepTarget = true)
                checkWritable(op, ndef?.isWritable, ndef?.maxCapacityBytes, source.size, ndef != null)?.let { return fail(op, it) }
                if (ndef!!.content == NdefContent.Empty) run(op, tag) { gateway.writeMessage(it, source) } else ask(tag, op, ContentSummary.of(ndef.content))
            }

            Op.ERASE -> {
                checkWritable(op, ndef?.isWritable, null, 0, ndef != null)?.let { return fail(op, it) }
                ask(tag, op, ContentSummary.of(ndef!!.content))
            }

            Op.LOCK -> when {
                ndef == null -> fail(op, OtherError.Nfc(NfcError.NoNdefSupport))
                ndef.isWritable == false -> fail(op, OtherError.Nfc(NfcError.ReadOnly))
                ndef.canMakeReadOnly == false -> fail(op, OtherError.Nfc(NfcError.LockUnsupported))
                else -> ask(tag, op, ContentSummary.of(ndef.content))
            }

            Op.FORMAT -> when {
                ndef != null -> fail(op, OtherError.AlreadyFormatted)
                !snapshot.formatable -> fail(op, OtherError.Nfc(NfcError.TechnologyUnavailable("NdefFormatable")))
                else -> ask(tag, op, null)
            }

            Op.SET_PASSWORD -> {
                if (snapshot.chip?.ntag == null) return fail(op, OtherError.Nfc(NfcError.ChipUnsupported))
                ask(tag, op, ndef?.content?.let(ContentSummary::of))
            }

            Op.READ_MEMORY, Op.REMOVE_PASSWORD -> Unit
        }
    }

    private fun checkWritable(op: Op, writable: Boolean?, capacity: Int?, needed: Int, hasNdef: Boolean): OtherError? = when {
        !hasNdef -> OtherError.Nfc(NfcError.NoNdefSupport)
        writable == false -> OtherError.Nfc(NfcError.ReadOnly)
        capacity != null && needed > capacity -> OtherError.Nfc(NfcError.TooLarge(needed, capacity))
        else -> null
    }

    private fun ask(tag: DiscoveredTag, op: Op, existing: ContentSummary?) {
        pendingTag = tag
        _phase.value = Phase.Confirm(op, existing)
    }

    fun confirm() {
        val c = _phase.value as? Phase.Confirm ?: return
        val tag = pendingTag ?: return
        pendingTag = null
        if (!busy.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                when (c.op) {
                    Op.COPY -> run(c.op, tag) { gateway.writeMessage(it, copySource ?: ByteArray(0)) }
                    Op.ERASE -> run(c.op, tag) { gateway.write(it, listOf(RecordSpec.Empty)) }
                    Op.LOCK -> run(c.op, tag) { gateway.makeReadOnly(it).map { null } }
                    Op.FORMAT -> run(c.op, tag) { gateway.formatAndWrite(it, listOf(RecordSpec.Empty)) }
                    Op.SET_PASSWORD -> run(c.op, tag) { gateway.setPassword(it, password, protectReads).map { null } }
                    else -> Unit
                }
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun run(op: Op, tag: DiscoveredTag, block: (DiscoveredTag) -> NfcResult<Verification?>) {
        _phase.value = Phase.Working(op)
        _phase.value = when (val r = withContext(io) { block(tag) }) {
            is NfcResult.Ok -> Phase.Done(op, verification = r.value)
            is NfcResult.Err -> Phase.Failed(op, OtherError.Nfc(r.error))
        }
        if (_phase.value is Phase.Done) password = ""
    }

    private fun fail(op: Op, error: OtherError, keepPrepare: Boolean = false, keepTarget: Boolean = false) {
        when {
            keepPrepare -> _prepareError.value = error
            keepTarget -> { _prepareError.value = error; _phase.value = Phase.Awaiting(op, copyTarget = true, copiedBytes = copySource?.size ?: 0) }
            else -> _phase.value = Phase.Failed(op, error)
        }
    }

    fun dismissResult() {
        if (_phase.value is Phase.Done || _phase.value is Phase.Failed) cancel()
    }
}

private inline fun <A, B> NfcResult<A>.map(f: (A) -> B): NfcResult<B> = when (this) {
    is NfcResult.Ok -> NfcResult.Ok(f(value))
    is NfcResult.Err -> this
}
