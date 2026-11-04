package com.tapandgo.feature.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.automation.engine.RunStatus
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkflowsScreen(onEdit: (Long) -> Unit, modifier: Modifier = Modifier) {
    val vm: WorkflowsViewModel = hiltViewModel()
    val list by vm.workflows.collectAsStateWithLifecycle()
    val runs by vm.runs.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    val copy = stringResource(R.string.template_copy_suffix)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.wf_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { onEdit(0) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.wf_new), Modifier.padding(start = 6.dp))
            }
        }
        if (list?.isEmpty() == true) item { MessageCard(title = stringResource(R.string.wf_empty), body = stringResource(R.string.wf_empty_detail)) }
        items(list.orEmpty(), key = { it.id }) { w ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Filled.AccountTree, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(w.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (w.workflow == null) stringResource(R.string.wf_invalid) else w.description.ifBlank { stringResource(R.string.wf_blocks, com.tapandgo.automation.engine.WorkflowEdit.count(w.workflow.blocks)) },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (w.workflow == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (running == w.id) CircularProgressIndicator(Modifier.padding(8.dp)) else
                        Button(onClick = { vm.run(w) }, enabled = w.workflow != null && running == null) { Text(stringResource(R.string.tasks_run)) }
                }
                Row(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                    IconButton(onClick = { onEdit(w.id) }) { Icon(Icons.Filled.Edit, stringResource(R.string.tasks_edit, w.name)) }
                    IconButton(onClick = { vm.duplicate(w.id, copy) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.template_duplicate, w.name)) }
                    IconButton(onClick = { pendingDelete = w.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.template_delete, w.name)) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.wf_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (runs.isNotEmpty()) TextButton(onClick = { vm.clearRuns() }) { Text(stringResource(R.string.tasks_log_clear)) }
            }
        }
        if (runs.isEmpty()) item { Text(stringResource(R.string.wf_history_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(runs, key = { "r${it.id}" }) { r ->
            Text(
                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(r.startTime))} · ${r.workflowName} · ${statusText(r.status)}" +
                    " · ${r.endTime - r.startTime} ms" + (r.errorMessage?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pendingDelete?.let { id ->
        Confirm(stringResource(R.string.wf_delete_title), stringResource(R.string.wf_delete_detail), stringResource(R.string.history_delete),
            onConfirm = { pendingDelete = null; vm.delete(id) }, onDismiss = { pendingDelete = null }, destructive = true)
    }
    result?.let { (name, r) ->
        AlertDialog(
            onDismissRequest = vm::dismissResult,
            title = { Text("$name · ${statusText(r.status.name)}") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    r.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (r.log.isEmpty()) Text(stringResource(R.string.wf_no_actions_ran))
                    r.log.forEach { s -> Text("${s.path}  ${s.actionId}  ${s.status}" + (s.detail?.let { " — $it" } ?: ""), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissResult) { Text(stringResource(R.string.writer_done)) } },
        )
    }
}

@Composable
private fun statusText(s: String): String = stringResource(
    when (runCatching { RunStatus.valueOf(s) }.getOrNull()) {
        RunStatus.SUCCESS -> R.string.wf_status_success
        RunStatus.STOPPED -> R.string.wf_status_stopped
        RunStatus.FAILED -> R.string.wf_status_failed
        RunStatus.TIMED_OUT -> R.string.wf_status_timeout
        RunStatus.LIMIT_EXCEEDED, null -> R.string.wf_status_limit
    },
)
