package dev.nfcstudio.app.core.nfc

import java.security.MessageDigest

/** What we could identify about the chip. Null fields mean "not reported", never guessed. */
data class ChipInfo(
    val vendor: String?,
    val name: String?,
    val family: Family,
    val totalBytes: Int? = null,
    val userBytes: Int? = null,
    val unitSize: Int? = null,
    val unitCount: Int? = null,
    /** NTAG21x layout when identified exactly; enables password operations. */
    val ntag: NtagLayout? = null,
) {
    enum class Family { NTAG, ULTRALIGHT, MIFARE_CLASSIC, ISO15693, ISO_DEP, FELICA, UNKNOWN }
}

/**
 * NTAG21x configuration layout (NXP NTAG213/215/216 datasheet, section 8.5.7):
 * CFG0 holds AUTH0 in byte 3, CFG1 holds ACCESS (bit 7 = PROT), followed by
 * PWD (4 bytes) and PACK (2 bytes) pages.
 */
data class NtagLayout(val model: String, val pages: Int, val userBytes: Int, val cfg0: Int) {
    val cfg1 get() = cfg0 + 1
    val pwdPage get() = cfg0 + 2
    val packPage get() = cfg0 + 3

    /** First user page; protecting from here covers the whole NDEF area. */
    val firstUserPage get() = 4
}

object ChipParsing {
    private val NTAG = mapOf(
        0x0F to NtagLayout("NTAG213", pages = 45, userBytes = 144, cfg0 = 0x29),
        0x11 to NtagLayout("NTAG215", pages = 135, userBytes = 504, cfg0 = 0x83),
        0x13 to NtagLayout("NTAG216", pages = 231, userBytes = 888, cfg0 = 0xE3),
    )

    /**
     * Parses the 8-byte GET_VERSION (0x60) response of NXP Type 2 chips.
     * @return null when the response is not a recognisable NXP version block.
     */
    fun parseNxpVersion(r: ByteArray): ChipInfo? {
        if (r.size != 8 || r[0] != 0x00.toByte() || r[1] != 0x04.toByte()) return null
        val type = r[2].toInt() and 0xFF
        val storage = r[6].toInt() and 0xFF
        return when (type) {
            0x04 -> NTAG[storage]?.let { l ->
                ChipInfo("NXP", l.model, ChipInfo.Family.NTAG, l.pages * 4, l.userBytes, 4, l.pages, l)
            } ?: when (storage) {
                0x0B -> ChipInfo("NXP", "NTAG210", ChipInfo.Family.NTAG, 80, 48, 4, 20)
                0x0E -> ChipInfo("NXP", "NTAG212", ChipInfo.Family.NTAG, 164, 128, 4, 41)
                else -> ChipInfo("NXP", "NTAG (unknown size)", ChipInfo.Family.NTAG)
            }

            0x03 -> when (storage) {
                0x0B -> ChipInfo("NXP", "MIFARE Ultralight EV1 (MF0UL11)", ChipInfo.Family.ULTRALIGHT, 80, 48, 4, 20)
                0x0E -> ChipInfo("NXP", "MIFARE Ultralight EV1 (MF0UL21)", ChipInfo.Family.ULTRALIGHT, 164, 128, 4, 41)
                else -> ChipInfo("NXP", "MIFARE Ultralight (variant)", ChipInfo.Family.ULTRALIGHT)
            }

            else -> ChipInfo("NXP", null, ChipInfo.Family.UNKNOWN)
        }
    }

    /** IC manufacturer from ISO/IEC 7816-6 registration (subset). */
    fun manufacturer(code: Int): String? = when (code) {
        0x02 -> "STMicroelectronics"
        0x04 -> "NXP"
        0x05 -> "Infineon"
        0x07 -> "Texas Instruments"
        0x16 -> "EM Microelectronic"
        0x1F -> "Melexis"
        else -> null
    }

    /**
     * Parses an ISO 15693 GET SYSTEM INFO (0x2B) response.
     * @param uidLsbFirst Tag.getId() for NfcV (least significant byte first).
     */
    fun parseNfcVSystemInfo(r: ByteArray, uidLsbFirst: ByteArray?): ChipInfo? {
        if (r.size < 10 || r[0].toInt() and 0x01 != 0) return null
        val info = r[1].toInt() and 0xFF
        var p = 10 // flags + info flags + 8-byte UID
        if (info and 0x01 != 0) p += 1 // DSFID
        if (info and 0x02 != 0) p += 1 // AFI
        var blocks: Int? = null
        var blockSize: Int? = null
        if (info and 0x04 != 0) {
            if (p + 2 > r.size) return null
            blocks = (r[p].toInt() and 0xFF) + 1
            blockSize = (r[p + 1].toInt() and 0x1F) + 1
            p += 2
        }
        val vendor = uidLsbFirst?.takeIf { it.size == 8 }?.let { manufacturer(it[6].toInt() and 0xFF) }
        val name = if (vendor == "NXP") "ICODE (ISO 15693)" else "ISO 15693 tag"
        val total = if (blocks != null && blockSize != null) blocks * blockSize else null
        return ChipInfo(vendor, name, ChipInfo.Family.ISO15693, total, total, blockSize, blocks)
    }

    /** NAK for Type 2 commands is a single 4-bit response (0x0..0x5 / 0x0A ACK). */
    fun isType2Nak(r: ByteArray) = r.size == 1 && (r[0].toInt() and 0x0F) != 0x0A

    /**
     * Derives the 4-byte PWD and 2-byte PACK from a user password.
     * Format (stable, documented in docs/tag-passwords.md): SHA-256(UTF-8 password),
     * bytes 0..3 = PWD, bytes 4..5 = PACK. Not interoperable with other apps.
     */
    fun derivePassword(password: String): Pair<ByteArray, ByteArray> {
        val h = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        return h.copyOfRange(0, 4) to h.copyOfRange(4, 6)
    }

    const val MIN_PASSWORD = 4
    const val MAX_PASSWORD = 64

    /** Pages written to set a password, in the safe order (AUTH0 last). */
    fun passwordWrites(layout: NtagLayout, pwd: ByteArray, pack: ByteArray, cfg0: ByteArray, cfg1: ByteArray, protectReads: Boolean): List<Pair<Int, ByteArray>> {
        require(pwd.size == 4 && pack.size == 2 && cfg0.size == 4 && cfg1.size == 4)
        val access = cfg1.copyOf().also { it[0] = (if (protectReads) it[0].toInt() or 0x80 else it[0].toInt() and 0x7F).toByte() }
        val auth = cfg0.copyOf().also { it[3] = layout.firstUserPage.toByte() }
        return listOf(
            layout.pwdPage to pwd,
            layout.packPage to byteArrayOf(pack[0], pack[1], 0, 0),
            layout.cfg1 to access,
            layout.cfg0 to auth,
        )
    }

    /** Pages written to remove protection: AUTH0 = 0xFF disables it; PROT cleared. */
    fun removalWrites(layout: NtagLayout, cfg0: ByteArray, cfg1: ByteArray): List<Pair<Int, ByteArray>> {
        val auth = cfg0.copyOf().also { it[3] = 0xFF.toByte() }
        val access = cfg1.copyOf().also { it[0] = (it[0].toInt() and 0x7F).toByte() }
        return listOf(layout.cfg0 to auth, layout.cfg1 to access, layout.pwdPage to ByteArray(4) { 0xFF.toByte() }, layout.packPage to ByteArray(4))
    }

    /** Whether AUTH0 currently enables password protection. */
    fun isProtected(layout: NtagLayout, cfg0: ByteArray) = (cfg0[3].toInt() and 0xFF) < layout.pages
}

/** Raw memory, one entry per page/block. Unreadable units are null. */
data class MemoryDump(val unitSize: Int, val units: List<String?>, val note: Note? = null) {
    enum class Note { STOPPED_AT_PROTECTED, PARTIAL_IO, UNSUPPORTED }
}
