package com.tapandgo.ui.components

import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tapandgo.R
import com.tapandgo.data.export.BackupService
import com.tapandgo.data.export.ImportResult

/** Result banner for export/import, with exact counts and rejection reasons. */
@Composable
fun BackupMessage(outcome: BackupService.Outcome, onDismiss: () -> Unit) {
    val (title, body, tone) = when (outcome) {
        is BackupService.Outcome.Exported -> Triple(
            stringResource(R.string.export_done),
            stringResource(R.string.export_done_detail, outcome.history, outcome.templates, outcome.profiles, outcome.library) +
                if (outcome.encrypted) " " + stringResource(R.string.export_encrypted_note) else "",
            Tone.SUCCESS,
        )

        is BackupService.Outcome.Imported -> Triple(
            stringResource(R.string.import_done),
            stringResource(
                R.string.import_done_detail,
                outcome.bundle.history.size,
                outcome.bundle.templates.size,
                outcome.bundle.profiles.size,
                outcome.bundle.library.size,
                outcome.bundle.skippedHistory + outcome.bundle.skippedTemplates + outcome.bundle.skippedProfiles + outcome.bundle.skippedLibrary,
            ),
            Tone.SUCCESS,
        )

        is BackupService.Outcome.Rejected -> Triple(
            stringResource(R.string.import_rejected),
            when (val r = outcome.reason) {
                ImportResult.TooLarge -> stringResource(R.string.import_too_large)
                ImportResult.NotJson -> stringResource(R.string.import_not_json)
                ImportResult.WrongFormat -> stringResource(R.string.import_wrong_format)
                is ImportResult.UnsupportedVersion -> stringResource(R.string.import_unsupported_version, r.version)
                ImportResult.TooManyItems -> stringResource(R.string.import_too_many)
                is ImportResult.Ok -> ""
            },
            Tone.ERROR,
        )

        BackupService.Outcome.NeedsPassword -> Triple(
            stringResource(R.string.backup_needs_password), stringResource(R.string.backup_needs_password_detail), Tone.NEUTRAL,
        )

        BackupService.Outcome.WrongPassword -> Triple(
            stringResource(R.string.import_rejected), stringResource(R.string.backup_wrong_password), Tone.ERROR,
        )

        BackupService.Outcome.IoFailed -> Triple(
            stringResource(R.string.backup_io_failed),
            stringResource(R.string.backup_io_failed_detail),
            Tone.ERROR,
        )
    }
    MessageCard(title = title, body = body, tone = tone) {
        TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
    }
}
