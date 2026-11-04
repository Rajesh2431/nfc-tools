package com.tapandgo.core.nfc

import com.tapandgo.core.common.Redact
import java.io.IOException

/**
 * Typed NFC failures. Every NFC I/O path maps failures here instead of
 * throwing through the UI (AGENTS rule 5).
 */
sealed interface NfcError {
    /** The tag left the field mid-operation. */
    data object TagLost : NfcError

    /** The Tag handle is from an older discovery session; scan again. */
    data object TagStale : NfcError

    /** Low-level I/O failure (timeout, transmission error). Detail is redacted. */
    data class Io(val detail: String? = null) : NfcError

    /** Tag exposes neither Ndef nor NdefFormatable. */
    data object NoNdefSupport : NfcError

    /** Tag reports it is not writable. */
    data object ReadOnly : NfcError

    /** Message does not fit the tag capacity reported by the platform. */
    data class TooLarge(val neededBytes: Int, val capacityBytes: Int) : NfcError

    /** Formatting a blank tag failed. */
    data class FormatFailed(val detail: String? = null) : NfcError

    /** Read-back content differs from what was requested. */
    data object VerifyFailed : NfcError

    /** The platform rejected the NDEF message as malformed. */
    data object InvalidMessage : NfcError

    /** The tag/device cannot be made read-only through the platform API. */
    data object LockUnsupported : NfcError

    /** makeReadOnly() returned false. */
    data object LockFailed : NfcError

    /** Password authentication was rejected by the tag. */
    data object WrongPassword : NfcError

    /** The tag is already password-protected; remove the password first. */
    data object AlreadyProtected : NfcError

    /** The chip could not be identified exactly enough for this operation. */
    data object ChipUnsupported : NfcError

    /** The tag does not expose the technology needed for this operation. */
    data class TechnologyUnavailable(val technology: String) : NfcError
}

/** Result type for every NFC operation. */
sealed interface NfcResult<out T> {
    data class Ok<T>(val value: T) : NfcResult<T>
    data class Err(val error: NfcError) : NfcResult<Nothing>
}

/** Thrown internally when a tag expected to support NDEF no longer does. */
class NdefNotSupportedException : Exception("tag does not expose Ndef")

fun Throwable.toNfcError(): NfcError = when (this) {
    is android.nfc.TagLostException -> NfcError.TagLost
    is NdefNotSupportedException -> NfcError.NoNdefSupport
    is android.nfc.FormatException -> NfcError.InvalidMessage
    is SecurityException -> NfcError.TagStale // "Tag ... is out of date"
    is IOException -> NfcError.Io(detail = Redact.platformMessage(message))
    else -> NfcError.Io(detail = Redact.throwable(this))
}

/** Runs [block], closing nothing itself, and maps any exception to [NfcResult.Err]. */
inline fun <T> nfcCatching(block: () -> NfcResult<T>): NfcResult<T> = try {
    block()
} catch (e: Exception) {
    NfcResult.Err(e.toNfcError())
}
