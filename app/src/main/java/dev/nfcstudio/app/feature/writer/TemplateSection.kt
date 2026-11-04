package dev.nfcstudio.app.feature.writer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import dev.nfcstudio.app.data.repository.Template
import dev.nfcstudio.app.data.repository.TemplateRepository

/** Save/load/duplicate/delete templates; loading puts records into the editor for review. */
@Composable
fun TemplateSection(writer: WriteViewModel) {
    val container = appContainer()
    val vm: TemplatesViewModel = viewModel(factory = viewModelFactory { initializer { container.templatesViewModel() } })
    val templates by vm.templates.collectAsStateWithLifecycle()
    val records by writer.records.collectAsStateWithLifecycle()
    var showSave by rememberSaveable { mutableStateOf(false) }
    var showLibrary by rememberSaveable { mutableStateOf(false) }
    var loadNotice by rememberSaveable { mutableStateOf<Int?>(null) }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.clearProblem(); showSave = true }, enabled = records.isNotEmpty()) {
            Text(stringResource(R.string.template_save))
        }
        OutlinedButton(onClick = { showLibrary = true }) {
            Text(pluralStringResource(R.plurals.template_library, templates.size, templates.size))
        }
    }
    loadNotice?.let { dropped ->
        Text(
            if (dropped == 0) stringResource(R.string.template_loaded) else stringResource(R.string.template_loaded_dropped, dropped),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSave) SaveDialog(vm, onSave = { name, desc -> vm.save(name, desc, records) { showSave = false } }, onDismiss = { showSave = false })
    if (showLibrary) {
        LibraryDialog(
            templates = templates,
            onLoad = { t ->
                showLibrary = false
                loadNotice = t.records?.let { writer.replaceRecords(it) }
            },
            onDuplicate = vm::duplicate,
            onDelete = vm::delete,
            onDismiss = { showLibrary = false },
        )
    }
}

@Composable
private fun SaveDialog(vm: TemplatesViewModel, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    val problem by vm.saveProblem.collectAsStateWithLifecycle()
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_save)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; vm.clearProblem() },
                    label = { Text(stringResource(R.string.template_name)) },
                    singleLine = true,
                    isError = problem != null,
                    supportingText = problem?.let { p -> { Text(stringResource(templateProblemText(p))) } },
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it; vm.clearProblem() },
                    label = { Text(stringResource(R.string.template_description)) },
                )
            }
        },
        confirmButton = { Button(onClick = { onSave(name, description) }) { Text(stringResource(R.string.template_save_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun LibraryDialog(
    templates: List<Template>,
    onLoad: (Template) -> Unit,
    onDuplicate: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val copySuffix = stringResource(R.string.template_copy_suffix)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.template_library_title)) },
        text = {
            if (templates.isEmpty()) {
                Text(stringResource(R.string.template_none))
            } else {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(templates, key = { it.id }) { t ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    t.records?.let { pluralStringResource(R.plurals.template_records, it.size, it.size) }
                                        ?: stringResource(R.string.template_invalid),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (t.records == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (t.description.isNotBlank()) Text(t.description, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onLoad(t) }, enabled = t.records != null) { Text(stringResource(R.string.template_load)) }
                            IconButton(onClick = { onDuplicate(t.id, copySuffix) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(R.string.template_duplicate, t.name))
                            }
                            IconButton(onClick = { pendingDelete = t.id }) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.template_delete, t.name))
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.writer_done)) } },
    )
    pendingDelete?.let { id ->
        Confirm(
            title = stringResource(R.string.template_delete_title),
            body = stringResource(R.string.template_delete_detail),
            confirm = stringResource(R.string.history_delete),
            destructive = true,
            onConfirm = { pendingDelete = null; onDelete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

fun templateProblemText(p: TemplateRepository.Problem): Int = when (p) {
    TemplateRepository.Problem.NAME_EMPTY -> R.string.template_name_empty
    TemplateRepository.Problem.NAME_TOO_LONG -> R.string.template_name_too_long
    TemplateRepository.Problem.DESCRIPTION_TOO_LONG -> R.string.template_description_too_long
    TemplateRepository.Problem.NO_RECORDS -> R.string.template_no_records
    TemplateRepository.Problem.INVALID_RECORD -> R.string.template_invalid
}
