package dev.nfcstudio.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.nfc.NfcError

/** Truthful, localized text for every [NfcError]; nothing collapses into a generic message. */
@Composable
fun NfcError.displayText(): String = when (this) {
    NfcError.TagLost -> stringResource(R.string.error_tag_lost)
    NfcError.TagStale -> stringResource(R.string.error_tag_stale)
    is NfcError.Io -> stringResource(R.string.error_io) + (detail?.let { " ($it)" } ?: "")
    NfcError.NoNdefSupport -> stringResource(R.string.error_no_ndef)
    NfcError.ReadOnly -> stringResource(R.string.error_read_only)
    is NfcError.TooLarge -> stringResource(R.string.error_too_large, neededBytes, capacityBytes)
    is NfcError.FormatFailed -> stringResource(R.string.error_format_failed) + (detail?.let { " ($it)" } ?: "")
    NfcError.VerifyFailed -> stringResource(R.string.error_verify)
    NfcError.InvalidMessage -> stringResource(R.string.error_invalid_message)
    NfcError.LockUnsupported -> stringResource(R.string.error_lock_unsupported)
    NfcError.LockFailed -> stringResource(R.string.error_lock_failed)
    is NfcError.TechnologyUnavailable -> stringResource(R.string.error_tech_unavailable, technology)
    NfcError.WrongPassword -> stringResource(R.string.error_wrong_password)
    NfcError.AlreadyProtected -> stringResource(R.string.error_already_protected)
    NfcError.ChipUnsupported -> stringResource(R.string.error_chip_unsupported)
}
