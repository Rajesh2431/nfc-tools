package dev.nfcstudio.app.feature.writer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.ndef.RecordProblem
import dev.nfcstudio.app.core.ndef.RecordSpec
import dev.nfcstudio.app.core.ndef.describe
import dev.nfcstudio.app.core.nfc.ContentSummary
import dev.nfcstudio.app.core.nfc.NfcReaderEffect
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.PlatformTag
import dev.nfcstudio.app.core.nfc.Verification
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.feature.writer.WriteViewModel.Operation
import dev.nfcstudio.app.feature.writer.WriteViewModel.Phase
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.NfcStatusCard
import dev.nfcstudio.app.ui.components.Tone
import dev.nfcstudio.app.ui.components.displayText

/**
 * Write screen: ordered record builder with exact encoded-size preview,
 * capacity/read-only checks, destructive-action confirmations and read-back
 * verification. Reader mode is armed for the whole screen; tags are ignored
 * unless an operation is waiting for one.
 */
@Composable
fun WriteScreen(
    modifier: Modifier = Modifier,
    templateActions: @Composable ColumnScope.(WriteViewModel) -> Unit = {},
) {
    val container = appContainer()
    val viewModel: WriteViewModel = viewModel(
        factory = viewModelFactory { initializer { container.writeViewModel() } },
    )
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val draftError by viewModel.draftError.collectAsStateWithLifecycle()
    val size by viewModel.estimatedSize.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()
    val nfcReady = nfcState == NfcState.READY

    NfcReaderEffect { tag -> viewModel.onTag(PlatformTag(tag)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_write),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (!nfcReady) NfcStatusCard(nfcState = nfcState)

        when (val p = phase) {
            is Phase.AwaitingTag -> MessageCard(
                title = stringResource(
                    when (p.operation) {
                        Operation.WRITE -> R.string.writer_waiting
                        Operation.ERASE -> R.string.erase_waiting
                        Operation.LOCK -> R.string.lock_waiting
                    },
                ),
                body = when (p.operation) {
                    Operation.WRITE -> pluralStringResource(R.plurals.writer_waiting_detail, records.size, records.size, size)
                    else -> stringResource(R.string.op_waiting_detail)
                },
                tone = Tone.WORKING,
            ) { OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.writer_cancel)) } }

            is Phase.Working -> MessageCard(
                title = stringResource(R.string.writer_writing),
                body = stringResource(R.string.writer_writing_detail),
                tone = Tone.WORKING,
            )

            is Phase.Success -> MessageCard(
                title = stringResource(
                    when (p.operation) {
                        Operation.WRITE -> R.string.writer_success_title
                        Operation.ERASE -> R.string.erase_success_title
                        Operation.LOCK -> R.string.lock_success_title
                    },
                ),
                body = when {
                    p.operation == Operation.LOCK -> stringResource(R.string.lock_success_detail)
                    p.verification == Verification.VERIFIED -> stringResource(R.string.writer_success_verified, p.bytes)
                    else -> stringResource(R.string.writer_success_unverified)
                },
                tone = Tone.SUCCESS,
            ) { Button(onClick = viewModel::dismissResult) { Text(stringResource(R.string.writer_done)) } }

            is Phase.Failed -> MessageCard(
                title = stringResource(R.string.writer_failed_title),
                body = p.error.displayText(),
                tone = Tone.ERROR,
            ) { Button(onClick = viewModel::dismissResult) { Text(stringResource(R.string.writer_done)) } }

            else -> Unit
        }

        val editable = phase == Phase.Editing
        RecordComposer(
            draft = draft,
            draftError = draftError,
            records = records,
            size = size,
            enabled = editable,
            onUpdate = viewModel::updateDraft,
            onAdd = { viewModel.addDraftRecord() },
            onRemove = viewModel::removeRecord,
            onMove = viewModel::moveRecord,
        )
        Button(
            onClick = viewModel::startWrite,
            enabled = editable && records.isNotEmpty() && nfcReady,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.writer_write)) }

        if (editable) templateActions(viewModel)

        TagOperationsCard(
            enabled = editable && nfcReady,
            onErase = viewModel::startErase,
            onLock = viewModel::requestLock,
        )
    }

    ConfirmationDialogs(phase = phase, viewModel = viewModel)
}

@Composable
private fun ConfirmationDialogs(phase: Phase, viewModel: WriteViewModel) {
    when (phase) {
        is Phase.ConfirmOverwrite -> Confirm(
            title = stringResource(R.string.overwrite_title),
            body = stringResource(R.string.overwrite_detail, describeContent(phase.existing)),
            confirm = stringResource(R.string.overwrite_confirm),
            destructive = true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::declineConfirmation,
        )

        Phase.ConfirmFormat -> Confirm(
            title = stringResource(R.string.format_title),
            body = stringResource(R.string.format_detail),
            confirm = stringResource(R.string.format_confirm),
            destructive = true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::declineConfirmation,
        )

        is Phase.ConfirmErase -> Confirm(
            title = stringResource(R.string.erase_title),
            body = stringResource(R.string.erase_detail, describeContent(phase.existing)),
            confirm = stringResource(R.string.erase_confirm),
            destructive = true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::declineConfirmation,
        )

        Phase.ConfirmLockIntent -> Confirm(
            title = stringResource(R.string.lock_intent_title),
            body = stringResource(R.string.lock_intent_detail),
            confirm = stringResource(R.string.lock_intent_confirm),
            destructive = true,
            onConfirm = viewModel::confirmLockIntent,
            onDismiss = viewModel::declineConfirmation,
        )

        is Phase.ConfirmLock -> Confirm(
            title = stringResource(R.string.lock_title),
            body = stringResource(R.string.lock_detail, describeContent(phase.existing)),
            confirm = stringResource(R.string.lock_confirm),
            destructive = true,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::declineConfirmation,
        )

        else -> Unit
    }
}

/** Shared destructive-confirmation dialog (spec § 9: one consistent component). */
@Composable
fun Confirm(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
fun describeContent(summary: ContentSummary): String = when (summary.kind) {
    ContentSummary.Kind.EMPTY -> stringResource(R.string.content_empty)
    ContentSummary.Kind.MALFORMED -> stringResource(R.string.content_malformed)
    ContentSummary.Kind.UNREADABLE -> stringResource(R.string.content_unreadable)
    ContentSummary.Kind.RECORDS -> pluralStringResource(
        R.plurals.content_records,
        summary.recordCount,
        summary.recordCount,
        summary.preview.orEmpty(),
    )
}

@Composable
private fun RecordComposer(
    draft: WriteViewModel.Draft,
    draftError: RecordProblem?,
    records: List<RecordSpec>,
    size: Int,
    enabled: Boolean,
    onUpdate: ((WriteViewModel.Draft) -> WriteViewModel.Draft) -> Unit,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.writer_compose), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WriteViewModel.DraftKind.entries.forEach { kind ->
                    FilterChip(
                        selected = draft.kind == kind,
                        enabled = enabled,
                        onClick = { onUpdate { d -> d.copy(kind = kind) } },
                        label = {
                            Text(
                                stringResource(
                                    when (kind) {
                                        WriteViewModel.DraftKind.TEXT -> R.string.writer_record_text
                                        WriteViewModel.DraftKind.URI -> R.string.writer_record_uri
                                        WriteViewModel.DraftKind.MIME -> R.string.writer_record_mime
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            val errorText = draftError?.let { stringResource(problemText(it)) }
            when (draft.kind) {
                WriteViewModel.DraftKind.TEXT -> {
                    Field(
                        value = draft.text,
                        label = stringResource(R.string.writer_text_label),
                        enabled = enabled,
                        error = errorText.takeIf { draftError == RecordProblem.TEXT_EMPTY || draftError == RecordProblem.TEXT_TOO_LONG },
                        minLines = 2,
                    ) { t -> onUpdate { it.copy(text = t) } }
                    Field(
                        value = draft.language,
                        label = stringResource(R.string.writer_language_label),
                        enabled = enabled,
                        error = errorText.takeIf { draftError == RecordProblem.LANGUAGE_INVALID },
                    ) { t -> onUpdate { it.copy(language = t) } }
                }

                WriteViewModel.DraftKind.URI -> Field(
                    value = draft.uri,
                    label = stringResource(R.string.writer_uri_label),
                    enabled = enabled,
                    placeholder = "https://example.com",
                    error = errorText,
                    keyboardType = KeyboardType.Uri,
                ) { t -> onUpdate { it.copy(uri = t) } }

                WriteViewModel.DraftKind.MIME -> {
                    Field(
                        value = draft.mimeType,
                        label = stringResource(R.string.writer_mime_label),
                        enabled = enabled,
                        error = errorText.takeIf { draftError == RecordProblem.MIME_INVALID },
                    ) { t -> onUpdate { it.copy(mimeType = t) } }
                    Field(
                        value = draft.mimePayload,
                        label = stringResource(R.string.writer_mime_payload_label),
                        enabled = enabled,
                        error = errorText.takeIf { draftError == RecordProblem.PAYLOAD_TOO_LONG },
                        minLines = 2,
                    ) { t -> onUpdate { it.copy(mimePayload = t) } }
                }
            }

            OutlinedButton(onClick = onAdd, enabled = enabled) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.writer_add), modifier = Modifier.padding(start = 6.dp))
            }

            if (records.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    pluralStringResource(R.plurals.writer_records, records.size, records.size),
                    style = MaterialTheme.typography.titleMedium,
                )
                records.forEachIndexed { index, record ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${index + 1}. ${kindName(record)}: ${record.describe()}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = { onMove(index, -1) }, enabled = enabled && index > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.writer_move_up))
                        }
                        IconButton(onClick = { onMove(index, 1) }, enabled = enabled && index < records.lastIndex) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.writer_move_down))
                        }
                        IconButton(onClick = { onRemove(index) }, enabled = enabled) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.writer_remove))
                        }
                    }
                }
                Text(
                    pluralStringResource(R.plurals.writer_estimated_size, size, size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun kindName(record: RecordSpec): String = stringResource(
    when (record) {
        is RecordSpec.Text -> R.string.writer_record_text
        is RecordSpec.Uri -> R.string.writer_record_uri
        is RecordSpec.Mime -> R.string.writer_record_mime
        RecordSpec.Empty -> R.string.record_empty
    },
)

@Composable
private fun Field(
    value: String,
    label: String,
    enabled: Boolean,
    error: String? = null,
    placeholder: String? = null,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        placeholder = placeholder?.let { { Text(it) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
    )
}

fun problemText(problem: RecordProblem): Int = when (problem) {
    RecordProblem.TEXT_EMPTY -> R.string.text_empty
    RecordProblem.TEXT_TOO_LONG -> R.string.text_too_long
    RecordProblem.LANGUAGE_INVALID -> R.string.language_invalid
    RecordProblem.URI_INVALID -> R.string.uri_invalid
    RecordProblem.MIME_INVALID -> R.string.mime_invalid
    RecordProblem.PAYLOAD_TOO_LONG -> R.string.payload_too_long
}

@Composable
private fun TagOperationsCard(enabled: Boolean, onErase: () -> Unit, onLock: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tag_ops_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.tag_ops_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onErase, enabled = enabled) { Text(stringResource(R.string.erase_start)) }
            OutlinedButton(
                onClick = onLock,
                enabled = enabled,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.lock_start)) }
        }
    }
}
