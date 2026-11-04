package dev.nfcstudio.app.data.repository

import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcError
import dev.nfcstudio.app.core.nfc.NfcResult
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.data.database.ContentKind
import dev.nfcstudio.app.data.database.ScanEntryEntity
import dev.nfcstudio.app.data.settings.AppSettings

/**
 * Pure mapping from a scan result to a history row, applying the privacy
 * settings: payload bytes and UID are stored only when enabled (spec 4.5).
 */
object ScanEntryFactory {

    const val MAX_SUMMARY = 500

    fun create(result: NfcResult<TagSnapshot>, settings: AppSettings, now: Long): ScanEntryEntity? {
        if (!settings.historyEnabled) return null
        return when (result) {
            is NfcResult.Err -> ScanEntryEntity(
                createdAt = now,
                summary = "",
                technologies = "",
                recordCount = 0,
                contentKind = ContentKind.ERROR.name,
                result = errorCode(result.error),
                ndefType = null,
                uidHex = null,
                payloadHex = null,
            )

            is NfcResult.Ok -> {
                val snapshot = result.value
                val content = snapshot.ndef?.content
                val records = (content as? NdefContent.Records)?.records.orEmpty()
                ScanEntryEntity(
                    createdAt = now,
                    summary = records.joinToString(" · ") { it.summarize(120) }.take(MAX_SUMMARY),
                    technologies = snapshot.technologies.joinToString(","),
                    recordCount = records.size,
                    contentKind = when (content) {
                        null -> ContentKind.NO_NDEF
                        NdefContent.Empty -> ContentKind.EMPTY
                        NdefContent.Malformed -> ContentKind.MALFORMED
                        NdefContent.Unreadable -> ContentKind.UNREADABLE
                        is NdefContent.Records -> ContentKind.RECORDS
                    }.name,
                    result = "OK",
                    ndefType = snapshot.ndef?.ndefType,
                    uidHex = snapshot.uidHex.takeIf { settings.storeUid },
                    payloadHex = (content as? NdefContent.Records)?.rawMessageHex?.takeIf { settings.savePayload },
                )
            }
        }
    }

    fun errorCode(error: NfcError): String = when (error) {
        NfcError.TagLost -> "TAG_LOST"
        NfcError.TagStale -> "TAG_STALE"
        is NfcError.Io -> "IO"
        NfcError.NoNdefSupport -> "NO_NDEF"
        NfcError.ReadOnly -> "READ_ONLY"
        is NfcError.TooLarge -> "TOO_LARGE"
        is NfcError.FormatFailed -> "FORMAT_FAILED"
        NfcError.VerifyFailed -> "VERIFY_FAILED"
        NfcError.InvalidMessage -> "INVALID_MESSAGE"
        NfcError.LockUnsupported -> "LOCK_UNSUPPORTED"
        NfcError.LockFailed -> "LOCK_FAILED"
        is NfcError.TechnologyUnavailable -> "TECH_UNAVAILABLE"
    }
}
