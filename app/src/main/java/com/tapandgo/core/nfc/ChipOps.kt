package com.tapandgo.core.nfc

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.NfcA
import android.nfc.tech.NfcV
import com.tapandgo.core.common.toHex
import java.io.IOException

/**
 * Chip-level operations over NfcA (Type 2) and NfcV (ISO 15693). Every
 * connection is closed in finally. Only documented read commands are sent
 * during identification; config writes happen only for an exactly identified
 * NTAG213/215/216 after explicit confirmation.
 */
internal object ChipOps {
    private const val GET_VERSION = 0x60.toByte()
    private const val READ = 0x30.toByte()
    private const val WRITE = 0xA2.toByte()
    private const val PWD_AUTH = 0x1B.toByte()

    /** Identification without side effects; null when nothing could be determined. */
    fun identify(tag: Tag): ChipInfo? {
        MifareUltralight.get(tag)?.let { ul ->
            val version = transceiveNfcA(tag) { it.transceive(byteArrayOf(GET_VERSION)) }
            version?.let(ChipParsing::parseNxpVersion)?.let { return it }
            return when (ul.type) {
                MifareUltralight.TYPE_ULTRALIGHT_C -> ChipInfo("NXP", "MIFARE Ultralight C", ChipInfo.Family.ULTRALIGHT, 192, 144, 4, 48)
                MifareUltralight.TYPE_ULTRALIGHT -> ChipInfo("NXP", "MIFARE Ultralight", ChipInfo.Family.ULTRALIGHT, 64, 48, 4, 16)
                else -> ChipInfo("NXP", "MIFARE Ultralight family", ChipInfo.Family.ULTRALIGHT)
            }
        }
        runCatching { MifareClassic.get(tag) }.getOrNull()?.let { mc ->
            return ChipInfo("NXP", "MIFARE Classic ${mc.size / 1024}K".replace(" 0K", " Mini"), ChipInfo.Family.MIFARE_CLASSIC, mc.size, null, 16, mc.blockCount)
        }
        NfcV.get(tag)?.let { v ->
            val r = runIo(v) { v.transceive(byteArrayOf(0x22, 0x2B) + (tag.id ?: ByteArray(0))) }
            return r?.let { ChipParsing.parseNfcVSystemInfo(it, tag.id) }
                ?: ChipInfo(tag.id?.takeIf { it.size == 8 }?.let { ChipParsing.manufacturer(it[6].toInt() and 0xFF) }, "ISO 15693 tag", ChipInfo.Family.ISO15693)
        }
        val techs = tag.techList.map { it.substringAfterLast('.') }
        return when {
            "IsoDep" in techs -> ChipInfo(null, "ISO 14443-4 (IsoDep) smart card / tag", ChipInfo.Family.ISO_DEP)
            "NfcF" in techs -> ChipInfo(null, "FeliCa (JIS 6319-4)", ChipInfo.Family.FELICA)
            else -> null
        }
    }

    private fun transceiveNfcA(tag: Tag, block: (NfcA) -> ByteArray): ByteArray? {
        val a = NfcA.get(tag) ?: return null
        return runIo(a) { block(a) }?.takeUnless { ChipParsing.isType2Nak(it) }
    }

    private fun runIo(tech: android.nfc.tech.TagTechnology, block: () -> ByteArray): ByteArray? = try {
        tech.connect()
        block()
    } catch (_: IOException) {
        null
    } catch (_: SecurityException) {
        null
    } finally {
        runCatching { tech.close() }
    }

    /** Reads all readable pages/blocks; stops at the first protected/unreadable unit. */
    fun dump(tag: Tag, chip: ChipInfo?): NfcResult<MemoryDump> = nfcCatching {
        val ul = MifareUltralight.get(tag)
        if (ul != null) {
            val pages = chip?.unitCount ?: 256
            val units = MutableList<String?>(0) { null }
            var note: MemoryDump.Note? = null
            ul.connect()
            try {
                var page = 0
                while (page < pages) {
                    val r = try { ul.readPages(page) } catch (e: IOException) {
                        if (e is android.nfc.TagLostException) throw e
                        note = if (chip?.unitCount == null) null else MemoryDump.Note.STOPPED_AT_PROTECTED
                        break
                    }
                    for (i in 0 until 4) if (page + i < pages) units += r.copyOfRange(i * 4, i * 4 + 4).toHex()
                    page += 4
                }
            } finally {
                runCatching { ul.close() }
            }
            return NfcResult.Ok(MemoryDump(4, units, note))
        }
        val v = NfcV.get(tag)
        val blocks = chip?.unitCount
        if (v != null && blocks != null) {
            val id = tag.id ?: ByteArray(0)
            val units = mutableListOf<String?>()
            var note: MemoryDump.Note? = null
            v.connect()
            try {
                for (b in 0 until minOf(blocks, 256)) {
                    val r = try { v.transceive(byteArrayOf(0x22, 0x20) + id + b.toByte()) } catch (e: IOException) {
                        if (e is android.nfc.TagLostException) throw e
                        note = MemoryDump.Note.PARTIAL_IO; break
                    }
                    units += if (r.isNotEmpty() && r[0].toInt() and 0x01 == 0) r.copyOfRange(1, r.size).toHex() else null
                }
            } finally {
                runCatching { v.close() }
            }
            return NfcResult.Ok(MemoryDump(chip.unitSize ?: 4, units, note))
        }
        NfcResult.Ok(MemoryDump(0, emptyList(), MemoryDump.Note.UNSUPPORTED))
    }

    private fun NfcA.cmd(vararg bytes: Byte): ByteArray {
        val r = transceive(bytes)
        if (ChipParsing.isType2Nak(r)) throw IOException("NAK")
        return r
    }

    private fun NfcA.readPage(page: Int): ByteArray = cmd(READ, page.toByte()).copyOf(4)
    private fun NfcA.writePage(page: Int, data: ByteArray) { cmd(WRITE, page.toByte(), *data) }

    /** @return false when authentication was rejected. */
    private fun NfcA.auth(pwd: ByteArray, pack: ByteArray): Boolean = try {
        val r = transceive(byteArrayOf(PWD_AUTH) + pwd)
        r.size == 2 && r.contentEquals(pack)
    } catch (e: IOException) {
        if (e is android.nfc.TagLostException) throw e
        false
    }

    fun setPassword(tag: Tag, layout: NtagLayout, password: String, protectReads: Boolean): NfcResult<Unit> = nfcCatching {
        val a = NfcA.get(tag) ?: return NfcResult.Err(NfcError.TechnologyUnavailable("NfcA"))
        val (pwd, pack) = ChipParsing.derivePassword(password)
        a.connect()
        try {
            val cfg0 = a.readPage(layout.cfg0)
            if (ChipParsing.isProtected(layout, cfg0)) return NfcResult.Err(NfcError.AlreadyProtected)
            val cfg1 = a.readPage(layout.cfg1)
            ChipParsing.passwordWrites(layout, pwd, pack, cfg0, cfg1, protectReads).forEach { (page, data) -> a.writePage(page, data) }
            // Verify: authentication with the new password must succeed.
            if (!a.auth(pwd, pack)) return NfcResult.Err(NfcError.VerifyFailed)
            NfcResult.Ok(Unit)
        } finally {
            runCatching { a.close() }
        }
    }

    fun removePassword(tag: Tag, layout: NtagLayout, password: String): NfcResult<Unit> = nfcCatching {
        val a = NfcA.get(tag) ?: return NfcResult.Err(NfcError.TechnologyUnavailable("NfcA"))
        val (pwd, pack) = ChipParsing.derivePassword(password)
        a.connect()
        try {
            if (!a.auth(pwd, pack)) return NfcResult.Err(NfcError.WrongPassword)
            val cfg0 = a.readPage(layout.cfg0)
            val cfg1 = a.readPage(layout.cfg1)
            ChipParsing.removalWrites(layout, cfg0, cfg1).forEach { (page, data) -> a.writePage(page, data) }
            NfcResult.Ok(Unit)
        } finally {
            runCatching { a.close() }
        }
    }
}
