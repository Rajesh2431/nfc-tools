package com.tapandgo.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tapandgo.R
import com.tapandgo.domain.task.FailureReason
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.ui.components.MessageCard
import java.text.DateFormat
import java.util.Date

/** Profile list, enable switches, run/edit/delete and the local execution log. */
@Composable
fun TasksScreen(onEdit: (Long) -> Unit, onRun: (Long) -> Unit, modifier: Modifier = Modifier) {
    val vm: TasksViewModel = hiltViewModel()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.tasks_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(onClick = { onEdit(0) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(stringResource(R.string.tasks_new), modifier = Modifier.padding(start = 6.dp))
            }
        }
        val list = profiles
        if (list != null && list.isEmpty()) {
            item { MessageCard(title = stringResource(R.string.tasks_empty), body = stringResource(R.string.tasks_empty_detail)) }
        }
        items(list.orEmpty(), key = { it.id }) { stored ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stored.name, style = MaterialTheme.typography.titleMedium)
                            val p = stored.profile
                            Text(
                                if (p == null) stringResource(R.string.tasks_invalid) else pluralStringResource(R.plurals.tasks_action_count, p.actions.size, p.actions.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (p == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val enabledLabel = stringResource(R.string.tasks_enabled_for, stored.name)
                        Switch(
                            checked = stored.profile?.enabled == true,
                            enabled = stored.profile != null,
                            onCheckedChange = { vm.setEnabled(stored.id, it) },
                            modifier = Modifier.semantics { contentDescription = enabledLabel },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(onClick = { onRun(stored.id) }, enabled = stored.profile?.enabled == true) { Text(stringResource(R.string.tasks_run)) }
                        IconButton(onClick = { onEdit(stored.id) }, enabled = stored.profile != null) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.tasks_edit, stored.name))
                        }
                        IconButton(onClick = { pendingDelete = stored.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.template_delete, stored.name))
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.tasks_log), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (log.isNotEmpty()) TextButton(onClick = { vm.clearLog() }) { Text(stringResource(R.string.tasks_log_clear)) }
            }
        }
        if (log.isEmpty()) {
            item { Text(stringResource(R.string.tasks_log_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(log, key = { "log-${it.id}" }) { entry ->
            val type = typeLabelOf(entry.actionType)?.let { stringResource(it) } ?: entry.actionType
            val reason = entry.reason?.let { r -> runCatching { FailureReason.valueOf(r) }.getOrNull() }?.let { " · " + stringResource(reasonLabel(it)) }.orEmpty()
            Text(
                "${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(entry.createdAt))} · ${entry.profileName} · " +
                    "#${entry.actionIndex + 1} $type · ${stringResource(outcomeLabel(entry.outcome))}$reason",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    pendingDelete?.let { id ->
        Confirm(
            title = stringResource(R.string.tasks_delete_title),
            body = stringResource(R.string.tasks_delete_detail),
            confirm = stringResource(R.string.history_delete),
            destructive = true,
            onConfirm = { pendingDelete = null; vm.delete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Shown on the Scan screen: profiles whose trigger matches. Running still needs a tap. */
@Composable
fun MatchingProfiles(names: List<Pair<Long, String>>, onRun: (Long) -> Unit) {
    if (names.isEmpty()) return
    MessageCard(title = stringResource(R.string.tasks_suggested), body = stringResource(R.string.tasks_suggested_detail)) {
        names.forEach { (id, name) -> OutlinedButton(onClick = { onRun(id) }) { Text(stringResource(R.string.tasks_run_named, name)) } }
    }
}
