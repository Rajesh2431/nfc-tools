package dev.nfcstudio.app.core.ndef

import dev.nfcstudio.app.core.ndef.NdefParser.RawRecord

/**
 * A record the user wants to write. Pure domain type, independent from
 * android.nfc so encoding, validation and size estimation are unit testable.
 */
sealed interface RecordSpec {

    /** Well-known Text record. Language is a BCP-47-style ASCII tag (<= 63 bytes). */
    data class Text(
        val text: String,
        val language: String = "en",
        val encodeAsUtf16: Boolean = false,
    ) : RecordSpec

    /** Well-known URI record using the NFC Forum abbreviation table. */
    data class Uri(val uri: String) : RecordSpec

    /** MIME media record with a UTF-8 text payload (e.g. text/plain, application/json). */
    data class Mime(val mimeType: String, val payload: String) : RecordSpec

    /** TNF_EMPTY record; used only to clear a tag. */
    data object Empty : RecordSpec
}

/** Pure encoding of a spec. Call [RecordValidator.validate] first. */
fun RecordSpec.toRawRecord(): RawRecord = when (this) {
    is RecordSpec.Text -> RawRecord(
        tnf = NdefParser.TNF_WELL_KNOWN,
        type = NdefParser.TEXT_TYPE,
        payload = NdefTextCodec.encode(text, language, encodeAsUtf16),
    )

    is RecordSpec.Uri -> RawRecord(
        tnf = NdefParser.TNF_WELL_KNOWN,
        type = NdefParser.URI_TYPE,
        payload = NdefUriCodec.encode(uri),
    )

    is RecordSpec.Mime -> RawRecord(
        tnf = NdefParser.TNF_MIME_MEDIA,
        type = mimeType.toByteArray(Charsets.US_ASCII),
        payload = payload.toByteArray(Charsets.UTF_8),
    )

    RecordSpec.Empty -> RawRecord(tnf = NdefParser.TNF_EMPTY, type = ByteArray(0), payload = ByteArray(0))
}

/** Short label for lists and confirmations. */
fun RecordSpec.describe(max: Int = 40): String = when (this) {
    is RecordSpec.Text -> text.take(max)
    is RecordSpec.Uri -> uri.take(max)
    is RecordSpec.Mime -> "$mimeType: ${payload.take(max)}"
    RecordSpec.Empty -> "empty record"
}

/** Validation problems; the UI maps each to a localized message. */
enum class RecordProblem { TEXT_EMPTY, TEXT_TOO_LONG, LANGUAGE_INVALID, URI_INVALID, MIME_INVALID, PAYLOAD_TOO_LONG }

object RecordValidator {

    /** Larger than any common NDEF tag (NTAG216 ≈ 868 B, DESFire EV ≈ 8 KiB). */
    const val MAX_PAYLOAD_BYTES = 8 * 1024

    private val LANGUAGE = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")
    private val MIME = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,126}/[a-z0-9][a-z0-9!#$&^_.+-]{0,126}$")

    fun isValidLanguage(language: String): Boolean = language.length <= 63 && LANGUAGE.matches(language)

    fun isValidMime(mime: String): Boolean = MIME.matches(mime)

    /** @return null when [spec] can be encoded and written safely. */
    fun validate(spec: RecordSpec): RecordProblem? = when (spec) {
        is RecordSpec.Text -> when {
            spec.text.isBlank() -> RecordProblem.TEXT_EMPTY
            !isValidLanguage(spec.language) -> RecordProblem.LANGUAGE_INVALID
            spec.text.toByteArray(if (spec.encodeAsUtf16) Charsets.UTF_16BE else Charsets.UTF_8).size >
                MAX_PAYLOAD_BYTES -> RecordProblem.TEXT_TOO_LONG

            else -> null
        }

        is RecordSpec.Uri -> when {
            UriPolicy.validate(spec.uri) != null -> RecordProblem.URI_INVALID
            spec.uri.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES -> RecordProblem.PAYLOAD_TOO_LONG
            else -> null
        }

        is RecordSpec.Mime -> when {
            !isValidMime(spec.mimeType) -> RecordProblem.MIME_INVALID
            spec.payload.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES -> RecordProblem.PAYLOAD_TOO_LONG
            else -> null
        }

        RecordSpec.Empty -> null
    }
}
