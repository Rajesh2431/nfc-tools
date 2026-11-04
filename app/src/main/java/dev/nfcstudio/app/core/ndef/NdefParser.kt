package dev.nfcstudio.app.core.ndef

import dev.nfcstudio.app.core.common.toAsciiPreview
import dev.nfcstudio.app.core.common.toHex

/**
 * Bounds-checked decoding of NDEF records. Input is untrusted (spec § 10):
 * every decode failure becomes a visible note, never an exception through the
 * UI layer.
 */
object NdefParser {

    const val TNF_EMPTY = 0
    const val TNF_WELL_KNOWN = 1
    const val TNF_MIME_MEDIA = 2
    const val TNF_ABSOLUTE_URI = 3
    const val TNF_EXTERNAL = 4
    const val TNF_UNKNOWN = 5
    const val TNF_UNCHANGED = 6

    /** Hex/ASCII previews are capped so huge payloads cannot stall the UI. */
    const val MAX_HEX_BYTES = 4096

    val TEXT_TYPE = byteArrayOf(0x54) // "T"
    val URI_TYPE = byteArrayOf(0x55) // "U"
    val SMART_POSTER_TYPE = byteArrayOf(0x53, 0x70) // "Sp"

    /** Platform-neutral record. */
    class RawRecord(val tnf: Int, val type: ByteArray, val payload: ByteArray, val id: ByteArray = ByteArray(0)) {
        override fun equals(other: Any?): Boolean = other is RawRecord &&
            tnf == other.tnf && type.contentEquals(other.type) &&
            payload.contentEquals(other.payload) && id.contentEquals(other.id)

        override fun hashCode(): Int = (tnf * 31 + type.contentHashCode()) * 31 + payload.contentHashCode()
    }

    fun decodeAll(records: List<RawRecord>): List<DecodedRecord> = records.map { decode(it) }

    fun decode(record: RawRecord): DecodedRecord = decode(record, allowNested = true)

    private fun decode(record: RawRecord, allowNested: Boolean): DecodedRecord {
        val base = DecodedRecord(
            kind = RecordKind.UNKNOWN,
            tnf = record.tnf,
            typeName = printableType(record.type),
            payloadSize = record.payload.size,
            rawHex = record.payload.copyOf(minOf(record.payload.size, MAX_HEX_BYTES)).toHex(),
            rawHexTruncated = record.payload.size > MAX_HEX_BYTES,
            asciiPreview = record.payload.copyOf(minOf(record.payload.size, MAX_HEX_BYTES)).toAsciiPreview(),
        )
        return try {
            when (record.tnf) {
                TNF_EMPTY -> base.copy(kind = RecordKind.EMPTY, rawHex = null, asciiPreview = null)
                TNF_WELL_KNOWN -> when {
                    record.type.contentEquals(TEXT_TYPE) -> {
                        val decoded = NdefTextCodec.decode(record.payload)
                        base.copy(kind = RecordKind.TEXT, text = decoded.text, language = decoded.language)
                    }

                    record.type.contentEquals(URI_TYPE) ->
                        base.copy(kind = RecordKind.URI, uri = NdefUriCodec.decode(record.payload))

                    record.type.contentEquals(SMART_POSTER_TYPE) && allowNested -> decodeSmartPoster(record, base)

                    else -> base.copy(note = "well-known type not decoded")
                }

                TNF_MIME_MEDIA -> base.copy(kind = RecordKind.MIME, mimeType = printableType(record.type))

                TNF_ABSOLUTE_URI -> base.copy(
                    kind = RecordKind.URI,
                    uri = printableType(record.type),
                    note = "absolute-URI record; payload kept as raw bytes",
                )

                TNF_EXTERNAL -> base.copy(note = "external type")
                TNF_UNKNOWN -> base.copy(note = "unknown type")
                TNF_UNCHANGED -> base.copy(note = "chunk continuation outside a chunked record")
                else -> base.copy(note = "reserved TNF ${record.tnf}")
            }
        } catch (e: NdefParseException) {
            base.copy(kind = RecordKind.UNKNOWN, note = e.message)
        }
    }

    /** Smart Poster: nested NDEF message with one URI and optional titles. */
    private fun decodeSmartPoster(record: RawRecord, base: DecodedRecord): DecodedRecord {
        val nested = NdefMessageCodec.parse(record.payload)
        val decoded = nested.map { decode(it, allowNested = false) }
        val uri = decoded.firstOrNull { it.kind == RecordKind.URI }?.uri
            ?: throw NdefParseException("smart poster without URI record")
        val title = decoded.firstOrNull { it.kind == RecordKind.TEXT }
        return base.copy(
            kind = RecordKind.SMART_POSTER,
            uri = uri,
            text = title?.text,
            language = title?.language,
            note = if (decoded.size > 2) "${decoded.size} nested records" else null,
        )
    }

    /** Type field as text when it is printable ASCII, else hex (bounded). */
    fun printableType(type: ByteArray): String? {
        if (type.isEmpty()) return null
        return if (type.all { (it.toInt() and 0xFF) in 0x20..0x7E }) {
            type.toString(Charsets.US_ASCII)
        } else {
            type.copyOf(minOf(type.size, 16)).toHex()
        }
    }
}
