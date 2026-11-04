package dev.nfcstudio.app.feature.writer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.ndef.NdefParser
import dev.nfcstudio.app.core.ndef.RecordProblem
import dev.nfcstudio.app.core.ndef.describe
import dev.nfcstudio.app.core.ndef.toRawRecord
import dev.nfcstudio.app.core.nfc.ContentSummary
import dev.nfcstudio.app.core.nfc.NfcReaderEffect
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.PlatformTag
import dev.nfcstudio.app.core.nfc.Verification
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.feature.writer.WriteViewModel.Phase
import dev.nfcstudio.app.ui.components.IconBadge
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.NfcStatusCard
import dev.nfcstudio.app.ui.components.SectionCard
import dev.nfcstudio.app.ui.components.Tone
import dev.nfcstudio.app.ui.components.displayText
import dev.nfcstudio.app.ui.components.recordIcon
import dev.nfcstudio.app.ui.components.recordKindLabel

/**
 * Write tab: build an ordered message from guided record types, see its exact
 * size, then write with overwrite/format confirmation and read-back
 * verification. Reader mode is armed for the whole screen.
 */
@Composable
fun WriteScreen(modifier: Modifier = Modifier) {
    val container = appContainer()
    val viewModel: WriteViewModel = viewModel(factory = viewModelFactory { initializer { container.writeViewModel() } })
    val records by viewModel.records.collectAsStateWithLifecycle()
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val size by viewModel.estimatedSize.collectAsStateWithLifecycle()
    val pending by container.pendingWrite.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()
    val nfcReady = nfcState == NfcState.READY
    var picking by rememberSaveable { mutableStateOf(false) }
    var formType by rememberSaveable { mutableStateOf<RecordType?>(null) }

    // Records handed over from another screen (e.g. a task tag) replace the draft.
    LaunchedEffect(pending) {
        pending?.let { viewModel.replaceRecords(it); container.pendingWrite.value = null }
    }

    NfcReaderEffect { tag -> viewModel.onTag(PlatformTag(tag)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!nfcReady) NfcStatusCard(nfcState = nfcState)
        PhaseCard(phase, records.size, size, viewModel)

        val editable = phase == Phase.Editing
        SectionCard(pluralStringResource(R.plurals.writer_records, records.size, records.size)) {
            if (records.isEmpty()) {
                Text(stringResource(R.string.writer_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            records.forEachIndexed { index, record ->
                if (index > 0) HorizontalDivider()
                val decoded = NdefParser.decode(record.toRawRecord())
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(recordIcon(decoded), container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(stringResource(recordKindLabel(decoded.kind)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(record.describe(60), style = MaterialTheme.typography.bodyLarge, maxLines = 2)
                    }
                    IconButton(onClick = { viewModel.moveRecord(index, -1) }, enabled = editable && index > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.writer_move_up))
                    }
                    IconButton(onClick = { viewModel.moveRecord(index, 1) }, enabled = editable && index < records.lastIndex) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.writer_move_down))
                    }
                    IconButton(onClick = { viewModel.removeRecord(index) }, enabled = editable) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.writer_remove))
                    }
                }
            }
            FilledTonalButton(onClick = { picking = true }, enabled = editable && records.size < WriteViewModel.MAX_RECORDS, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.writer_add_record), modifier = Modifier.padding(start = 6.dp))
            }
            if (records.isNotEmpty()) {
                Text(
                    pluralStringResource(R.plurals.writer_estimated_size, size, size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Button(
            onClick = viewModel::startWrite,
            enabled = editable && records.isNotEmpty() && nfcReady,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.Filled.Nfc, contentDescription = null)
            Text(stringResource(R.string.writer_write), modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
        if (editable) TemplateSection(viewModel)
    }

    if (picking) RecordTypePicker(onPick = { picking = false; formType = it }, onDismiss = { picking = false })
    formType?.let { t -> RecordForm(type = t, onAdd = viewModel::addRecord, onDismiss = { formType = null }) }
    ConfirmationDialogs(phase = phase, viewModel = viewModel)
}

@Composable
private fun PhaseCard(phase: Phase, count: Int, size: Int, viewModel: WriteViewModel) {
    when (phase) {
        is Phase.AwaitingTag -> MessageCard(
            title = stringResource(R.string.writer_waiting),
            body = pluralStringResource(R.plurals.writer_waiting_detail, count, count, size),
            tone = Tone.WORKING,
        ) { OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.writer_cancel)) } }

        is Phase.Working -> MessageCard(title = stringResource(R.string.writer_writing), body = stringResource(R.string.writer_writing_detail)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        is Phase.Success -> MessageCard(
            title = stringResource(R.string.writer_success_title),
            body = if (phase.verification == Verification.VERIFIED) {
                stringResource(R.string.writer_success_verified, phase.bytes)
            } else {
                stringResource(R.string.writer_success_unverified)
            },
            tone = Tone.SUCCESS,
        ) { Button(onClick = viewModel::dismissResult) { Text(stringResource(R.string.writer_done)) } }

        is Phase.Failed -> MessageCard(title = stringResource(R.string.writer_failed_title), body = phase.error.displayText(), tone = Tone.ERROR) {
            Button(onClick = viewModel::dismissResult) { Text(stringResource(R.string.writer_done)) }
        }

        else -> Unit
    }
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
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
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
    ContentSummary.Kind.RECORDS -> pluralStringResource(R.plurals.content_records, summary.recordCount, summary.recordCount, summary.preview.orEmpty())
}

fun problemText(problem: RecordProblem): Int = when (problem) {
    RecordProblem.TEXT_EMPTY -> R.string.text_empty
    RecordProblem.TEXT_TOO_LONG -> R.string.text_too_long
    RecordProblem.LANGUAGE_INVALID -> R.string.language_invalid
    RecordProblem.URI_INVALID -> R.string.uri_invalid
    RecordProblem.MIME_INVALID -> R.string.mime_invalid
    RecordProblem.PAYLOAD_TOO_LONG -> R.string.payload_too_long
    RecordProblem.PAYLOAD_INVALID, RecordProblem.EXTERNAL_TYPE_INVALID -> R.string.form_invalid
}
