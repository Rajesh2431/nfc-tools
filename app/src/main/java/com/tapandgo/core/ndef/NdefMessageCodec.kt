package com.tapandgo.core.ndef

import com.tapandgo.core.ndef.NdefParser.RawRecord

/**
 * Pure NDEF message (de)serialisation per NFC Forum NDEF 1.0.
 *
 * Used for exact size estimation, history payload re-decoding and nested
 * Smart Poster messages. The parser never allocates more than the remaining
 * input and rejects chunked records (the platform reassembles chunks before
 * handing messages to apps).
 */
object NdefMessageCodec {

    private const val MB = 0x80
    private const val ME = 0x40
    private const val CF = 0x20
    private const val SR = 0x10
    private const val IL = 0x08
    private const val TNF_MASK = 0x07

    /** Upper bound on records in one message; protects against crafted input. */
    const val MAX_RECORDS = 255

    fun encode(records: List<RawRecord>): ByteArray {
        require(records.isNotEmpty()) { "an NDEF message needs at least one record" }
        val out = java.io.ByteArrayOutputStream()
        records.forEachIndexed { index, r ->
            val short = r.payload.size <= 255
            var header = r.tnf and TNF_MASK
            if (index == 0) header = header or MB
            if (index == records.lastIndex) header = header or ME
            if (short) header = header or SR
            if (r.id.isNotEmpty()) header = header or IL
            require(r.type.size <= 255) { "type longer than 255 bytes" }
            require(r.id.size <= 255) { "id longer than 255 bytes" }
            out.write(header)
            out.write(r.type.size)
            if (short) {
                out.write(r.payload.size)
            } else {
                val n = r.payload.size
                out.write(n ushr 24 and 0xFF); out.write(n ushr 16 and 0xFF)
                out.write(n ushr 8 and 0xFF); out.write(n and 0xFF)
            }
            if (r.id.isNotEmpty()) out.write(r.id.size)
            out.write(r.type)
            out.write(r.id)
            out.write(r.payload)
        }
        return out.toByteArray()
    }

    /** @throws NdefParseException for any structural problem. */
    fun parse(bytes: ByteArray): List<RawRecord> {
        if (bytes.isEmpty()) throw NdefParseException("empty NDEF message")
        val records = mutableListOf<RawRecord>()
        var pos = 0
        fun need(n: Int, what: String) {
            if (n < 0 || pos.toLong() + n > bytes.size) throw NdefParseException("truncated $what at offset $pos")
        }
        while (pos < bytes.size) {
            if (records.size >= MAX_RECORDS) throw NdefParseException("more than $MAX_RECORDS records")
            need(2, "record header")
            val header = bytes[pos].toInt() and 0xFF
            val typeLength = bytes[pos + 1].toInt() and 0xFF
            pos += 2
            if (records.isEmpty() && header and MB == 0) throw NdefParseException("first record lacks MB flag")
            if (header and CF != 0) throw NdefParseException("chunked records are not supported")
            val payloadLength: Long
            if (header and SR != 0) {
                need(1, "payload length")
                payloadLength = (bytes[pos].toInt() and 0xFF).toLong()
                pos += 1
            } else {
                need(4, "payload length")
                payloadLength = ((bytes[pos].toLong() and 0xFF) shl 24) or
                    ((bytes[pos + 1].toLong() and 0xFF) shl 16) or
                    ((bytes[pos + 2].toLong() and 0xFF) shl 8) or
                    (bytes[pos + 3].toLong() and 0xFF)
                pos += 4
            }
            var idLength = 0
            if (header and IL != 0) {
                need(1, "id length")
                idLength = bytes[pos].toInt() and 0xFF
                pos += 1
            }
            if (payloadLength > bytes.size - pos) throw NdefParseException("payload length $payloadLength exceeds message")
            need(typeLength, "type"); val type = bytes.copyOfRange(pos, pos + typeLength); pos += typeLength
            need(idLength, "id"); val id = bytes.copyOfRange(pos, pos + idLength); pos += idLength
            val pl = payloadLength.toInt()
            need(pl, "payload"); val payload = bytes.copyOfRange(pos, pos + pl); pos += pl
            records += RawRecord(tnf = header and TNF_MASK, type = type, payload = payload, id = id)
            if (header and ME != 0) {
                if (pos != bytes.size) throw NdefParseException("trailing bytes after ME record")
                return records
            }
        }
        throw NdefParseException("last record lacks ME flag")
    }
}
