package dev.nfcstudio.app.core.ndef

import dev.nfcstudio.app.core.common.hexToBytesOrNull
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

    /** MIME media record with binary payload (Wi-Fi WSC, Bluetooth OOB). Hex keeps equality value-based. */
    data class MimeBinary(val mimeType: String, val payloadHex: String) : RecordSpec

    /** NFC Forum external type, e.g. "android.com:pkg" (app launch) or the task-tag token type. */
    data class External(val type: String, val payloadHex: String) : RecordSpec

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

    is RecordSpec.MimeBinary -> RawRecord(
        tnf = NdefParser.TNF_MIME_MEDIA,
        type = mimeType.toByteArray(Charsets.US_ASCII),
        payload = payloadHex.hexToBytesOrNull() ?: ByteArray(0),
    )

    is RecordSpec.External -> RawRecord(
        tnf = NdefParser.TNF_EXTERNAL,
        type = type.toByteArray(Charsets.US_ASCII),
        payload = payloadHex.hexToBytesOrNull() ?: ByteArray(0),
    )

    RecordSpec.Empty -> RawRecord(tnf = NdefParser.TNF_EMPTY, type = ByteArray(0), payload = ByteArray(0))
}

/** Short label for lists and confirmations. */
fun RecordSpec.describe(max: Int = 40): String = when (this) {
    is RecordSpec.Text -> text.take(max)
    is RecordSpec.Uri -> uri.take(max)
    is RecordSpec.Mime -> if (mimeType == "text/vcard") {
        payload.lineSequence().firstOrNull { it.startsWith("FN:") }?.removePrefix("FN:")?.take(max) ?: mimeType
    } else {
        "$mimeType: ${payload.take(max)}"
    }
    is RecordSpec.MimeBinary -> when (mimeType) {
        RichRecords.WIFI_MIME -> runCatching { RichRecords.decodeWifi(payloadHex.hexToBytesOrNull()!!).first().value }.getOrDefault(mimeType)
        RichRecords.BLUETOOTH_MIME -> runCatching { RichRecords.decodeBluetooth(payloadHex.hexToBytesOrNull()!!).joinToString(" ") { it.value } }.getOrDefault(mimeType)
        else -> mimeType
    }.take(max)
    is RecordSpec.External -> (payloadHex.hexToBytesOrNull()?.toString(Charsets.US_ASCII) ?: type).take(max)
    RecordSpec.Empty -> "empty record"
}

/** Validation problems; the UI maps each to a localized message. */
enum class RecordProblem { TEXT_EMPTY, TEXT_TOO_LONG, LANGUAGE_INVALID, URI_INVALID, MIME_INVALID, PAYLOAD_TOO_LONG, PAYLOAD_INVALID, EXTERNAL_TYPE_INVALID }

object RecordValidator {

    /** Larger than any common NDEF tag (NTAG216 ≈ 868 B, DESFire EV ≈ 8 KiB). */
    const val MAX_PAYLOAD_BYTES = 8 * 1024

    private val LANGUAGE = Regex("^[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*$")
    /** NFC Forum external type: "domain:type", lowercase, printable. */
    private val EXTERNAL = Regex("^[a-z0-9.-]{1,100}:[a-z0-9._-]{1,100}$")
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

        is RecordSpec.MimeBinary -> {
            val bytes = spec.payloadHex.hexToBytesOrNull()
            when {
                !isValidMime(spec.mimeType) -> RecordProblem.MIME_INVALID
                bytes == null || bytes.isEmpty() -> RecordProblem.PAYLOAD_INVALID
                bytes.size > MAX_PAYLOAD_BYTES -> RecordProblem.PAYLOAD_TOO_LONG
                spec.mimeType == RichRecords.WIFI_MIME && runCatching { RichRecords.decodeWifi(bytes) }.isFailure -> RecordProblem.PAYLOAD_INVALID
                spec.mimeType == RichRecords.BLUETOOTH_MIME && runCatching { RichRecords.decodeBluetooth(bytes) }.isFailure -> RecordProblem.PAYLOAD_INVALID
                else -> null
            }
        }

        is RecordSpec.External -> {
            val bytes = spec.payloadHex.hexToBytesOrNull()
            when {
                !EXTERNAL.matches(spec.type) -> RecordProblem.EXTERNAL_TYPE_INVALID
                bytes == null -> RecordProblem.PAYLOAD_INVALID
                bytes.size > MAX_PAYLOAD_BYTES -> RecordProblem.PAYLOAD_TOO_LONG
                spec.type == RichRecords.AAR_TYPE && !RichRecords.isValidPackage(bytes.toString(Charsets.US_ASCII)) -> RecordProblem.PAYLOAD_INVALID
                spec.type == RichRecords.TASK_TYPE && !RichRecords.isValidToken(bytes.toString(Charsets.US_ASCII)) -> RecordProblem.PAYLOAD_INVALID
                else -> null
            }
        }

        RecordSpec.Empty -> null
    }
}
