package com.tapandgo.core.ndef

import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.NdefParser.RawRecord

/** Pure helpers behind the developer NDEF Lab (decoder, raw builder, hexdump). */
object NdefLab {
    const val MAX_INPUT_BYTES = 64 * 1024

    sealed interface Decoded {
        data class Ok(val records: List<RawRecord>, val unwrappedFromTlv: Boolean) : Decoded
        data class Error(val reason: String) : Decoded
    }

    /**
     * Decodes hex as an NDEF message. Also accepts a Type 2 tag memory area
     * that starts with an NDEF TLV (03 len … / 03 FF hi lo …), as found in
     * memory dumps.
     */
    fun decodeHex(hex: String): Decoded {
        val bytes = hex.hexToBytesOrNull() ?: return Decoded.Error("not valid hex (0-9, a-f, even length)")
        if (bytes.isEmpty()) return Decoded.Error("no bytes")
        if (bytes.size > MAX_INPUT_BYTES) return Decoded.Error("more than ${MAX_INPUT_BYTES / 1024} KiB")
        val tlv = unwrapTlv(bytes)
        return try {
            Decoded.Ok(NdefMessageCodec.parse(tlv ?: bytes), unwrappedFromTlv = tlv != null)
        } catch (e: NdefParseException) {
            Decoded.Error(e.message ?: "malformed NDEF")
        }
    }

    /** Returns the NDEF message inside a leading NDEF TLV, skipping NULL TLVs, or null. */
    fun unwrapTlv(b: ByteArray): ByteArray? {
        var i = 0
        while (i < b.size && b[i] == 0x00.toByte()) i++
        if (i >= b.size || b[i] != 0x03.toByte() || i + 1 >= b.size) return null
        val first = b[i + 1].toInt() and 0xFF
        val (len, start) = if (first == 0xFF) {
            if (i + 3 >= b.size) return null
            (((b[i + 2].toInt() and 0xFF) shl 8) or (b[i + 3].toInt() and 0xFF)) to i + 4
        } else first to i + 2
        if (len == 0 || start + len > b.size) return null
        return b.copyOfRange(start, start + len)
    }

    /** Encodes raw specs; null with a reason when any record is invalid. */
    fun encode(records: List<RecordSpec.Raw>): Pair<ByteArray?, Int?> {
        if (records.isEmpty()) return null to -1
        records.forEachIndexed { i, r -> if (RecordValidator.validate(r) != null) return null to i }
        return NdefMessageCodec.encode(records.map { it.toRawRecord() }) to null
    }

    /** Classic hexdump: offset, 16 hex bytes, printable ASCII. */
    fun hexdump(bytes: ByteArray, width: Int = 16, maxBytes: Int = 4096): String = buildString {
        val n = minOf(bytes.size, maxBytes)
        var off = 0
        while (off < n) {
            val end = minOf(off + width, n)
            append("%04X  ".format(off))
            for (i in off until off + width) {
                if (i < end) append("%02X ".format(bytes[i])) else append("   ")
            }
            append(' ')
            for (i in off until end) {
                val c = bytes[i].toInt() and 0xFF
                append(if (c in 0x20..0x7E) c.toChar() else '.')
            }
            if (end < n) append('\n')
            off = end
        }
        if (bytes.size > maxBytes) append("\n… ${bytes.size - maxBytes} more bytes")
    }

    /** ASCII text → hex (for type fields), rejecting non-ASCII. */
    fun asciiToHex(s: String): String? = if (s.all { it.code in 0x20..0x7E }) s.toByteArray(Charsets.US_ASCII).joinToString("") { "%02x".format(it) } else null

    fun utf8ToHex(s: String): String = s.toByteArray(Charsets.UTF_8).joinToString("") { "%02x".format(it) }
}
