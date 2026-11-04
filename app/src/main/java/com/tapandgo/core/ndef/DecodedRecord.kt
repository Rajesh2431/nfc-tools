package com.tapandgo.core.ndef

/** Decoded record categories the UI can present truthfully. */
enum class RecordKind { TEXT, URI, MIME, SMART_POSTER, CONTACT, WIFI, BLUETOOTH, APP, TASK, EXTERNAL, EMPTY, UNKNOWN }

/**
 * Platform-independent view of one NDEF record, decoded as far as it is safe
 * to decode. Unknown and malformed records stay visible with a [note] instead
 * of crashing or being hidden.
 */
data class DecodedRecord(
    val kind: RecordKind,
    val tnf: Int,
    val typeName: String? = null,
    val text: String? = null,
    val language: String? = null,
    val uri: String? = null,
    val mimeType: String? = null,
    val payloadSize: Int = 0,
    /** Hex of the payload, capped at [NdefParser.MAX_HEX_BYTES] bytes. */
    val rawHex: String? = null,
    val rawHexTruncated: Boolean = false,
    /** Printable-ASCII preview of the payload (non-printables shown as '.'). */
    val asciiPreview: String? = null,
    val note: String? = null,
    /** Structured fields for rich records (contact, Wi-Fi, Bluetooth, app, task). */
    val details: List<Detail> = emptyList(),
) {
    /** One-line human summary used in previews, history and confirmations. */
    fun summarize(maxLength: Int = 48): String = when (kind) {
        RecordKind.TEXT -> text.orEmpty().take(maxLength)
        RecordKind.URI -> uri.orEmpty().take(maxLength)
        RecordKind.SMART_POSTER -> (uri ?: text).orEmpty().take(maxLength)
        RecordKind.MIME -> "$mimeType (${payloadSize}B)".take(maxLength)
        RecordKind.CONTACT, RecordKind.WIFI, RecordKind.BLUETOOTH, RecordKind.APP, RecordKind.TASK ->
            (details.firstOrNull { !it.secret }?.value ?: typeName.orEmpty()).take(maxLength)
        RecordKind.EXTERNAL -> (typeName ?: "external").take(maxLength)
        RecordKind.EMPTY -> "empty"
        RecordKind.UNKNOWN -> (typeName?.takeIf { it.isNotBlank() } ?: "unknown").take(maxLength)
    }
}
