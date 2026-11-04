package dev.nfcstudio.app.core.ndef

/** Thrown when a payload does not conform to the NFC Forum record definitions. */
class NdefParseException(message: String) : Exception(message)

/**
 * NFC Forum Text record payload codec (Text RTD 1.0).
 * Layout: status byte | language (US-ASCII) | text (UTF-8 or UTF-16).
 * Status byte: bit 7 = encoding (0 = UTF-8, 1 = UTF-16), bit 6 reserved,
 * bits 5..0 = language length. Bounds-checked against untrusted data.
 */
object NdefTextCodec {

    private const val UTF16_FLAG: Int = 0x80
    private const val LANGUAGE_MASK: Int = 0x3F

    /** @throws IllegalArgumentException for an invalid language code; validate first with [RecordValidator]. */
    fun encode(text: String, language: String = "en", utf16: Boolean = false): ByteArray {
        require(RecordValidator.isValidLanguage(language)) { "invalid language code" }
        val languageBytes = language.toByteArray(Charsets.US_ASCII)
        val textBytes = if (utf16) text.toByteArray(Charsets.UTF_16BE) else text.toByteArray(Charsets.UTF_8)
        val status = (if (utf16) UTF16_FLAG else 0) or languageBytes.size
        return byteArrayOf(status.toByte()) + languageBytes + textBytes
    }

    fun decode(payload: ByteArray): DecodedText {
        if (payload.isEmpty()) throw NdefParseException("empty text payload")
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = status and UTF16_FLAG != 0
        val languageLength = status and LANGUAGE_MASK
        if (1 + languageLength > payload.size) {
            throw NdefParseException("language length $languageLength exceeds payload")
        }
        val language = payload.copyOfRange(1, 1 + languageLength).toString(Charsets.US_ASCII)
        val body = payload.copyOfRange(1 + languageLength, payload.size)
        // UTF-16 text may carry a BOM; Charsets.UTF_16 honours it and defaults to BE.
        val text = body.toString(if (isUtf16) Charsets.UTF_16 else Charsets.UTF_8)
        return DecodedText(text = text, language = language, isUtf16 = isUtf16)
    }

    data class DecodedText(val text: String, val language: String, val isUtf16: Boolean)
}
