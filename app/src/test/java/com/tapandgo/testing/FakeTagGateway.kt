package com.tapandgo.testing

import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.nfc.DiscoveredTag
import com.tapandgo.core.nfc.MemoryDump
import com.tapandgo.core.nfc.NfcResult
import com.tapandgo.core.nfc.TagGateway
import com.tapandgo.core.nfc.TagSnapshot
import com.tapandgo.core.nfc.Verification

/** Test-only handle; production code only ever sees PlatformTag. */
object TestTag : DiscoveredTag

/** Test double recording every operation; lives in test sources only (AGENTS rule 8). */
class FakeTagGateway(
    var readResult: NfcResult<TagSnapshot>,
    var writeResult: NfcResult<Verification> = NfcResult.Ok(Verification.VERIFIED),
    var formatResult: NfcResult<Verification> = NfcResult.Ok(Verification.NOT_POSSIBLE),
    var lockResult: NfcResult<Unit> = NfcResult.Ok(Unit),
    var dumpResult: NfcResult<MemoryDump> = NfcResult.Ok(MemoryDump(4, listOf("04a1b2c3"))),
    var passwordResult: NfcResult<Unit> = NfcResult.Ok(Unit),
) : TagGateway {
    val writes = mutableListOf<List<RecordSpec>>()
    val rawWrites = mutableListOf<ByteArray>()
    val passwords = mutableListOf<Pair<String, Boolean>>()
    val removedPasswords = mutableListOf<String>()
    var identifyRequested = false
    /** Successive reads can return different tags (copy source → target). */
    val readQueue = ArrayDeque<NfcResult<TagSnapshot>>()
    val formats = mutableListOf<List<RecordSpec>>()
    var locks = 0

    override fun read(tag: DiscoveredTag, identifyChip: Boolean): NfcResult<TagSnapshot> {
        identifyRequested = identifyRequested || identifyChip
        return readQueue.removeFirstOrNull() ?: readResult
    }

    override fun writeMessage(tag: DiscoveredTag, message: ByteArray): NfcResult<Verification> {
        rawWrites += message
        return writeResult
    }

    override fun dumpMemory(tag: DiscoveredTag) = dumpResult

    override fun setPassword(tag: DiscoveredTag, password: String, protectReads: Boolean): NfcResult<Unit> {
        passwords += password to protectReads
        return passwordResult
    }

    override fun removePassword(tag: DiscoveredTag, password: String): NfcResult<Unit> {
        removedPasswords += password
        return passwordResult
    }
    override fun write(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> {
        writes += specs
        return writeResult
    }

    override fun formatAndWrite(tag: DiscoveredTag, specs: List<RecordSpec>): NfcResult<Verification> {
        formats += specs
        return formatResult
    }

    override fun makeReadOnly(tag: DiscoveredTag): NfcResult<Unit> {
        locks++
        return lockResult
    }
}
