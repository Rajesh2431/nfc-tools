package com.tapandgo.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tapandgo.R
import com.tapandgo.domain.task.ConfirmationPolicy
import com.tapandgo.domain.task.SettingsPanel
import com.tapandgo.ui.components.MessageCard

/** Profile editor: name, policy, optional scan trigger and ordered allowlisted actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorScreen(profileId: Long, onDone: () -> Unit, onWriteTag: () -> Unit, modifier: Modifier = Modifier) {
    val vm: ProfileEditorViewModel = hiltViewModel()
    val draft by vm.draft.collectAsStateWithLifecycle()
    val actionDraft by vm.actionDraft.collectAsStateWithLifecycle()
    val actionProblem by vm.actionProblem.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    LaunchedEffect(saved) { if (saved) onDone() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (missing) {
            MessageCard(title = stringResource(R.string.tasks_invalid)) { OutlinedButton(onClick = onDone) { Text(stringResource(R.string.writer_done)) } }
            return@Column
        }
        val d = draft ?: return@Column

        OutlinedTextField(
            value = d.name,
            onValueChange = { v -> vm.update { it.copy(name = v) } },
            label = { Text(stringResource(R.string.template_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = d.enabled, role = Role.Switch) { v -> vm.update { it.copy(enabled = v) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.profile_enabled), Modifier.weight(1f))
            Switch(checked = d.enabled, onCheckedChange = null)
        }

        Text(stringResource(R.string.profile_policy), style = MaterialTheme.typography.titleSmall)
        ConfirmationPolicy.entries.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = d.policy == p, role = Role.RadioButton) { vm.update { it.copy(policy = p) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = d.policy == p, onClick = null)
                Text(
                    stringResource(if (p == ConfirmationPolicy.EXTERNAL_ONLY) R.string.policy_external else R.string.policy_every),
                    Modifier.padding(start = 12.dp),
                )
            }
        }

        Text(stringResource(R.string.profile_trigger), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.profile_trigger_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TriggerKind.entries.forEach { k ->
                FilterChip(
                    selected = d.triggerKind == k,
                    onClick = { vm.update { it.copy(triggerKind = k) } },
                    label = {
                        Text(
                            stringResource(
                                when (k) {
                                    TriggerKind.NONE -> R.string.trigger_none
                                    TriggerKind.TEXT -> R.string.trigger_text
                                    TriggerKind.HOST -> R.string.trigger_host
                                    TriggerKind.TASK_TAG -> R.string.trigger_task_tag
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (d.triggerKind == TriggerKind.TASK_TAG) {
            Text(stringResource(R.string.task_tag_explain), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = {
                    vm.writeTaskTag(onWriteTag)
                },
                enabled = d.actions.isNotEmpty() && d.name.isNotBlank(),
            ) { Text(stringResource(R.string.task_tag_write)) }
        }
        val autoAllowed = d.triggerKind != TriggerKind.NONE && d.policy == ConfirmationPolicy.EXTERNAL_ONLY
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = d.autoRun && autoAllowed, enabled = autoAllowed, role = Role.Switch) { v -> vm.update { it.copy(autoRun = v) } },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.profile_auto_run))
                Text(
                    stringResource(if (autoAllowed) R.string.profile_auto_run_detail else R.string.profile_auto_run_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = d.autoRun && autoAllowed, onCheckedChange = null, enabled = autoAllowed)
        }
        if (d.triggerKind == TriggerKind.TEXT || d.triggerKind == TriggerKind.HOST) {
            OutlinedTextField(
                value = d.triggerValue,
                onValueChange = { v -> vm.update { it.copy(triggerValue = v) } },
                label = { Text(stringResource(if (d.triggerKind == TriggerKind.TEXT) R.string.trigger_text_value else R.string.trigger_host_value)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        HorizontalDivider()
        Text(stringResource(R.string.profile_actions), style = MaterialTheme.typography.titleMedium)
        d.actions.forEachIndexed { i, action ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. " + describe(action), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.moveAction(i, -1) }, enabled = i > 0) {
                    Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.writer_move_up))
                }
                IconButton(onClick = { vm.moveAction(i, 1) }, enabled = i < d.actions.lastIndex) {
                    Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.writer_move_down))
                }
                IconButton(onClick = { vm.removeAction(i) }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.writer_remove))
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_add_action), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionDraft.Type.entries.forEach { t ->
                        FilterChip(
                            selected = actionDraft.type == t,
                            onClick = { vm.updateAction { ActionDraft(type = t) } },
                            label = { Text(stringResource(typeLabel(t))) },
                        )
                    }
                }
                ActionFields(actionDraft, vm::updateAction)
                actionProblem?.let { Text(stringResource(actionProblemText(it)), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = { vm.addAction() }) { Text(stringResource(R.string.writer_add)) }
            }
        }

        problem?.let { Text(stringResource(profileProblemText(it)), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.save() }) { Text(stringResource(R.string.template_save_confirm)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.writer_cancel)) }
        }
    }
}

@Composable
private fun ActionFields(d: ActionDraft, update: ((ActionDraft) -> ActionDraft) -> Unit) {
    @Composable
    fun field(value: String, label: Int, minLines: Int = 1, set: (ActionDraft, String) -> ActionDraft) = OutlinedTextField(
        value = value,
        onValueChange = { v -> update { set(it, v) } },
        label = { Text(stringResource(label)) },
        minLines = minLines,
        singleLine = minLines == 1,
        modifier = Modifier.fillMaxWidth(),
    )
    when (d.type) {
        ActionDraft.Type.OPEN_URL -> field(d.a, R.string.field_url) { x, v -> x.copy(a = v) }
        ActionDraft.Type.OPEN_MAP -> field(d.a, R.string.field_map_query) { x, v -> x.copy(a = v) }
        ActionDraft.Type.DISPLAY_TEXT -> field(d.a, R.string.field_text, 3) { x, v -> x.copy(a = v) }
        ActionDraft.Type.DRAFT_EMAIL -> {
            field(d.a, R.string.field_email) { x, v -> x.copy(a = v) }
            field(d.b, R.string.field_subject) { x, v -> x.copy(b = v) }
            field(d.c, R.string.field_body, 2) { x, v -> x.copy(c = v) }
        }

        ActionDraft.Type.DRAFT_SMS -> {
            field(d.a, R.string.field_phone) { x, v -> x.copy(a = v) }
            field(d.b, R.string.field_body, 2) { x, v -> x.copy(b = v) }
        }

        ActionDraft.Type.SHOW_CONTACT -> {
            field(d.a, R.string.field_name) { x, v -> x.copy(a = v) }
            field(d.b, R.string.field_phone_optional) { x, v -> x.copy(b = v) }
            field(d.c, R.string.field_email_optional) { x, v -> x.copy(c = v) }
        }

        ActionDraft.Type.OPEN_APP -> field(d.a, R.string.field_package) { x, v -> x.copy(a = v) }
        ActionDraft.Type.OPEN_SETTINGS -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Column {
                SettingsPanel.entries.forEach { p ->
                    Row(
                        Modifier.selectable(selected = d.panel == p, role = Role.RadioButton) { update { it.copy(panel = p) } },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = d.panel == p, onClick = null)
                        Text(stringResource(panelLabel(p)), Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}
