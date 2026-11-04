package com.tapandgo.core.nfc

import android.nfc.FormatException
import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import com.tapandgo.core.common.toHex
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.toRawRecord
import java.io.IOException

/** Opaque handle to a discovered tag, so view models stay JVM-testable. */
interface DiscoveredTag

/** The real platform tag. */
class PlatformTag(val tag: Tag) : DiscoveredTag

/** Outcome of read-back verification after a write. */
enum class Verification {
    /** Read back in the same session; bytes match exactly. */
    VERIFIED,

    /** Write completed but read-back was impossible (e.g. tag left, or freshly formatted). */
    NOT_POSSIBLE,
}

/**
 * Every tag I/O operation. Implementations close all technology connections
 * and return typed results; they never throw.
 */
interface TagGateway {
    /** @param identifyChip also sends read-only chip identification commands (Scan only). */
    fun read(tag: DiscoveredTag, identifyChip: Boolean = false): NfcResult<TagSnapshot>
    fun write(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification>

    /** Writes an already-encoded NDEF message (tag copy). Same checks and verification as [write]. */
    fun writeMessage(tag: DiscoveredTag, message: ByteArray): NfcResult<Verification>
    fun dumpMemory(tag: DiscoveredTag): NfcResult<MemoryDump>
    fun setPassword(tag: DiscoveredTag, password: String, protectReads: Boolean): NfcResult<Unit>
    fun removePassword(tag: DiscoveredTag, password: String): NfcResult<Unit>
    fun formatAndWrite(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification>
    fun makeReadOnly(tag: DiscoveredTag): NfcResult<Unit>
}

/** android.nfc implementation of [TagGateway]. */
object PlatformTagGateway : TagGateway {

    private fun DiscoveredTag.platform(): Tag = (this as PlatformTag).tag

    override fun read(tag: DiscoveredTag, identifyChip: Boolean): NfcResult<TagSnapshot> = nfcCatching {
        val t = tag.platform()
        val ndef = Ndef.get(t)
        val ndefSnapshot = ndef?.let(::readNdef)
        // Identification runs last: a NAK can halt some chips until they re-enter the field.
        val chip = if (identifyChip) runCatching { ChipOps.identify(t) }.getOrNull() else null
        NfcResult.Ok(
            TagSnapshot(
                uidHex = t.id?.takeIf { it.isNotEmpty() }?.toHex(),
                technologies = t.techList.map { it.substringAfterLast('.') },
                ndef = ndefSnapshot,
                formatable = NdefFormatable.get(t) != null,
                techDetails = TagInspector.inspect(t),
                chip = chip,
            ),
        )
    }

    /**
     * Live read with fallback to the discovery-time cache. Malformed content
     * is reported as such so the writer always asks before overwriting it.
     */
    private fun readNdef(ndef: Ndef): NdefSnapshot {
        var fromCache = false
        var writable: Boolean? = null
        var canLock: Boolean? = null
        val content: NdefContent = try {
            ndef.connect()
            writable = runCatching { ndef.isWritable }.getOrNull()
            canLock = runCatching { ndef.canMakeReadOnly() }.getOrNull()
            try {
                contentOf(ndef.ndefMessage)
            } catch (_: FormatException) {
                NdefContent.Malformed
            }
        } catch (_: IOException) {
            fromCache = true
            writable = runCatching { ndef.isWritable }.getOrNull()
            ndef.cachedNdefMessage?.let(::contentOf) ?: NdefContent.Unreadable
        } finally {
            runCatching { ndef.close() }
        }
        return NdefSnapshot(
            ndefType = ndef.type,
            maxCapacityBytes = ndef.maxSize.takeIf { it > 0 },
            isWritable = writable,
            canMakeReadOnly = canLock,
            content = content,
            fromCache = fromCache,
        )
    }

    private fun contentOf(message: NdefMessage?): NdefContent {
        if (message == null) return NdefContent.Empty
        val bytes = message.toByteArray()
        val raws = message.records.map {
            NdefParser.RawRecord(it.tnf.toInt(), it.type, it.payload ?: ByteArray(0), it.id ?: ByteArray(0))
        }
        // A single TNF_EMPTY record is how a cleared tag looks.
        if (raws.size == 1 && raws[0].tnf == NdefParser.TNF_EMPTY) return NdefContent.Empty
        return NdefContent.Records(NdefParser.decodeAll(raws), bytes.toHex(), bytes.size)
    }

    private fun buildMessage(specs: List<RecordSpec>): NdefMessage =
        NdefMessage(NdefMessageCodec.encode(specs.map { it.toRawRecord() }))

    override fun write(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> =
        nfcCatching { writeMessage(tag, NdefMessageCodec.encode(specs.map { it.toRawRecord() })) }

    override fun writeMessage(tag: DiscoveredTag, message: ByteArray): NfcResult<Verification> = nfcCatching {
        val ndef = Ndef.get(tag.platform()) ?: return NfcResult.Err(NfcError.NoNdefSupport)
        val ndefMessage = NdefMessage(message)
        val expected = ndefMessage.toByteArray()
        ndef.connect()
        try {
            if (!ndef.isWritable) return NfcResult.Err(NfcError.ReadOnly)
            val capacity = ndef.maxSize
            if (capacity > 0 && expected.size > capacity) {
                return NfcResult.Err(NfcError.TooLarge(expected.size, capacity))
            }
            ndef.writeNdefMessage(ndefMessage)
            // Read back over RF in the same session; never the discovery cache.
            val readBack = try {
                ndef.ndefMessage
            } catch (_: IOException) {
                return NfcResult.Ok(Verification.NOT_POSSIBLE)
            } catch (_: FormatException) {
                return NfcResult.Err(NfcError.VerifyFailed)
            }
            if (readBack != null && readBack.toByteArray().contentEquals(expected)) {
                NfcResult.Ok(Verification.VERIFIED)
            } else {
                NfcResult.Err(NfcError.VerifyFailed)
            }
        } finally {
            runCatching { ndef.close() }
        }
    }

    override fun formatAndWrite(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> =
        nfcCatching {
            val formatable = NdefFormatable.get(tag.platform())
                ?: return NfcResult.Err(NfcError.TechnologyUnavailable("NdefFormatable"))
            val message = buildMessage(specs)
            formatable.connect()
            try {
                formatable.format(message)
            } catch (e: IOException) {
                if (e is android.nfc.TagLostException) throw e
                return NfcResult.Err(NfcError.FormatFailed(com.tapandgo.core.common.Redact.platformMessage(e.message)))
            } finally {
                runCatching { formatable.close() }
            }
            // The Tag handle still describes the pre-format, non-NDEF tag, so a
            // read-back needs a fresh discovery. Report that truthfully.
            NfcResult.Ok(Verification.NOT_POSSIBLE)
        }

    override fun dumpMemory(tag: DiscoveredTag): NfcResult<MemoryDump> = nfcCatching {
        val t = tag.platform()
        ChipOps.dump(t, runCatching { ChipOps.identify(t) }.getOrNull())
    }

    private fun ntagLayout(t: Tag): NtagLayout? = runCatching { ChipOps.identify(t) }.getOrNull()?.ntag

    override fun setPassword(tag: DiscoveredTag, password: String, protectReads: Boolean): NfcResult<Unit> = nfcCatching {
        val t = tag.platform()
        val layout = ntagLayout(t) ?: return NfcResult.Err(NfcError.ChipUnsupported)
        ChipOps.setPassword(t, layout, password, protectReads)
    }

    override fun removePassword(tag: DiscoveredTag, password: String): NfcResult<Unit> = nfcCatching {
        val t = tag.platform()
        val layout = ntagLayout(t) ?: return NfcResult.Err(NfcError.ChipUnsupported)
        ChipOps.removePassword(t, layout, password)
    }

    override fun makeReadOnly(tag: DiscoveredTag): NfcResult<Unit> = nfcCatching {
        val ndef = Ndef.get(tag.platform()) ?: return NfcResult.Err(NfcError.NoNdefSupport)
        ndef.connect()
        try {
            if (!ndef.canMakeReadOnly()) return NfcResult.Err(NfcError.LockUnsupported)
            if (ndef.makeReadOnly()) NfcResult.Ok(Unit) else NfcResult.Err(NfcError.LockFailed)
        } finally {
            runCatching { ndef.close() }
        }
    }
}
