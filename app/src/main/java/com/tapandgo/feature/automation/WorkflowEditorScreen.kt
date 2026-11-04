package com.tapandgo.feature.automation

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.automation.engine.Block
import com.tapandgo.automation.engine.Condition
import com.tapandgo.automation.engine.WorkflowEdit.ListRef
import com.tapandgo.automation.engine.WorkflowEdit.Slot
import com.tapandgo.automation.engine.WorkflowJson
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.SectionCard

/** Which block the dialog edits: a new block appended to [ref], or [index] within it. */
private data class Editing(val ref: ListRef, val index: Int?, val block: Block)

@Composable
fun WorkflowEditorScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: WorkflowEditorViewModel = hiltViewModel()
    val wf by vm.workflow.collectAsStateWithLifecycle()
    val description by vm.description.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Editing?>(null) }
    var adding by remember { mutableStateOf<ListRef?>(null) }
    LaunchedEffect(saved) { if (saved) onDone() }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(wf.name, vm::rename, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.template_name) + " *") })
        OutlinedTextField(description, vm::describe, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.template_description)) })
        Row(Modifier.fillMaxWidth().toggleable(wf.settings.stopOnError, role = Role.Switch, onValueChange = vm::setStopOnError), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.wf_stop_on_error), Modifier.weight(1f))
            Switch(wf.settings.stopOnError, null)
        }
        SectionCard(stringResource(R.string.wf_blocks_title)) {
            BlockList(wf.blocks, ListRef(), depth = 0, vm = vm, onEdit = { editing = it }, onAdd = { adding = it })
        }
        Text(stringResource(R.string.wf_variables_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        problem?.let { Text(stringResource(problemText(it)), color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::save) { Text(stringResource(R.string.template_save_confirm)) }
            OutlinedButton(onClick = onDone) { Text(stringResource(R.string.writer_cancel)) }
        }
    }

    adding?.let { ref ->
        AddBlockDialog(onPick = { b -> adding = null; editing = Editing(ref, null, b) }, onDismiss = { adding = null })
    }
    editing?.let { e ->
        BlockEditorDialog(e.block, onSave = { b ->
            if (e.index == null) vm.insert(e.ref, b) else vm.replace(e.ref, e.index, b)
            editing = null
        }, onDismiss = { editing = null })
    }
}

@Composable
private fun BlockList(blocks: List<Block>, ref: ListRef, depth: Int, vm: WorkflowEditorViewModel, onEdit: (Editing) -> Unit, onAdd: (ListRef) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEachIndexed { i, b ->
            BlockRow(b, i, blocks.size, ref, depth, vm, onEdit, onAdd)
        }
        TextButton(onClick = { onAdd(ref) }, enabled = depth < WorkflowJson.MAX_DEPTH) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(stringResource(R.string.wf_add_block), Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun BlockRow(b: Block, i: Int, size: Int, ref: ListRef, depth: Int, vm: WorkflowEditorViewModel, onEdit: (Editing) -> Unit, onAdd: (ListRef) -> Unit) {
    val child = { slot: Slot, elseIf: Int? -> ListRef(ref.steps + (i to slot), elseIf) }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(role = Role.Button) { onEdit(Editing(ref, i, b)) }) {
            IconBadge(blockIcon(b), size = 32.dp, container = if (b.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
            Text(
                blockSummary(b), Modifier.padding(start = 10.dp).weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = if (b.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
        }
        Row {
            IconButton(onClick = { vm.move(ref, i, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.writer_move_up)) }
            IconButton(onClick = { vm.move(ref, i, 1) }, enabled = i < size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.writer_move_down)) }
            IconButton(onClick = { vm.toggle(ref, i) }) {
                Icon(if (b.enabled) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, stringResource(if (b.enabled) R.string.wf_disable else R.string.wf_enable))
            }
            IconButton(onClick = { vm.duplicate(ref, i) }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.wf_duplicate_block)) }
            IconButton(onClick = { vm.remove(ref, i) }) { Icon(Icons.Filled.Close, stringResource(R.string.writer_remove)) }
        }
        val nested = Modifier.padding(start = 12.dp)
        when (b) {
            is Block.If -> Column(nested) {
                SlotLabel(R.string.wf_then); BlockList(b.then, child(Slot.THEN, null), depth + 1, vm, onEdit, onAdd)
                b.elseIfs.forEachIndexed { n, e ->
                    SlotLabel(R.string.wf_else_if, ConditionUi.describe(e.condition))
                    BlockList(e.then, child(Slot.THEN, n), depth + 1, vm, onEdit, onAdd)
                }
                SlotLabel(R.string.wf_else); BlockList(b.otherwise, child(Slot.OTHERWISE, null), depth + 1, vm, onEdit, onAdd)
            }
            is Block.Repeat -> Column(nested) { BlockList(b.body, child(Slot.BODY, null), depth + 1, vm, onEdit, onAdd) }
            is Block.While -> Column(nested) { BlockList(b.body, child(Slot.BODY, null), depth + 1, vm, onEdit, onAdd) }
            else -> Unit
        }
    }
}

@Composable
private fun SlotLabel(res: Int, extra: String? = null) {
    Text(stringResource(res) + (extra?.let { " $it" } ?: ""), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp))
}

private fun blockIcon(b: Block): ImageVector = when (b) {
    is Block.Action -> ActionCatalog.byId(b.actionId)?.icon ?: Icons.Filled.DataObject
    is Block.If -> Icons.Filled.CallSplit
    is Block.Repeat -> Icons.Filled.Repeat
    is Block.While -> Icons.Filled.Loop
    is Block.Delay -> Icons.Filled.HourglassEmpty
    is Block.SetVariable -> Icons.Filled.DataObject
    is Block.Break -> Icons.Filled.ExitToApp
    is Block.Continue -> Icons.Filled.SkipNext
    is Block.Stop -> Icons.Filled.Stop
}

@Composable
private fun blockSummary(b: Block): String = when (b) {
    is Block.Action -> (ActionCatalog.byId(b.actionId)?.let { stringResource(it.title) } ?: b.actionId) +
        b.params.entries.joinToString(", ", prefix = if (b.params.isEmpty()) "" else " · ") { "${it.key}=${it.value.take(30)}" }
    is Block.If -> stringResource(R.string.wf_if) + " " + ConditionUi.describe(b.condition)
    is Block.Repeat -> stringResource(R.string.wf_repeat_n, b.times)
    is Block.While -> stringResource(R.string.wf_while) + " " + ConditionUi.describe(b.condition)
    is Block.Delay -> stringResource(R.string.wf_delay_ms, b.millis)
    is Block.SetVariable -> "\${${b.name}} = ${b.value.take(40)}"
    is Block.Break -> stringResource(R.string.wf_break)
    is Block.Continue -> stringResource(R.string.wf_continue)
    is Block.Stop -> stringResource(R.string.wf_stop)
}

private fun problemText(p: WorkflowJson.Problem) = when (p) {
    WorkflowJson.Problem.NAME -> R.string.template_name_empty
    WorkflowJson.Problem.TOO_MANY_BLOCKS -> R.string.wf_problem_size
    WorkflowJson.Problem.TOO_DEEP -> R.string.wf_problem_depth
    WorkflowJson.Problem.UNKNOWN_ACTION -> R.string.wf_problem_action
    WorkflowJson.Problem.BAD_VARIABLE -> R.string.wf_problem_variable
}

@Composable
private fun AddBlockDialog(onPick: (Block) -> Unit, onDismiss: () -> Unit) {
    val defaultCond = Condition.Compare("\${BATTERY_LEVEL}", Condition.Op.LESS, "20")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wf_add_block)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                item { Text(stringResource(R.string.wf_group_logic), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                val logic = listOf(
                    R.string.wf_if to Block.If(defaultCond, emptyList()),
                    R.string.wf_repeat to Block.Repeat("3", emptyList(), indexVariable = "I"),
                    R.string.wf_while to Block.While(defaultCond, emptyList()),
                    R.string.wf_delay to Block.Delay("1000"),
                    R.string.wf_set to Block.SetVariable("NAME", ""),
                    R.string.wf_break to Block.Break(),
                    R.string.wf_continue to Block.Continue(),
                    R.string.wf_stop to Block.Stop(),
                )
                items(logic) { (label, block) ->
                    PickRow(blockIcon(block), stringResource(label)) { onPick(block) }
                }
                item { Text(stringResource(R.string.wf_group_actions), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                items(ActionCatalog.all) { spec ->
                    PickRow(spec.icon, stringResource(spec.title)) {
                        onPick(Block.Action(spec.id, spec.params.associate { p -> p.key to (p.options?.first() ?: "") }))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun PickRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, size = 32.dp)
        Text(label, Modifier.padding(start = 12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BlockEditorDialog(block: Block, onSave: (Block) -> Unit, onDismiss: () -> Unit) {
    var b by remember { mutableStateOf(block) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(blockSummary(block).substringBefore(" ·").take(40)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val x = b) {
                    is Block.Action -> {
                        val spec = ActionCatalog.byId(x.actionId)
                        spec?.params?.forEach { p ->
                            if (p.options != null) {
                                Text(stringResource(p.label), style = MaterialTheme.typography.labelMedium)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    p.options.forEach { o -> FilterChip(x.params[p.key] == o, { b = x.copy(params = x.params + (p.key to o)) }, { Text(o) }) }
                                }
                            } else {
                                OutlinedTextField(x.params[p.key].orEmpty(), { v -> b = x.copy(params = x.params + (p.key to v)) }, modifier = Modifier.fillMaxWidth(),
                                    label = { Text(stringResource(p.label)) }, placeholder = p.hint?.let { { Text(it) } })
                            }
                        }
                        spec?.note?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        OutlinedTextField(x.outputVariable.orEmpty(), { v -> b = x.copy(outputVariable = v.ifBlank { null }) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.wf_output_var)) })
                        OutlinedTextField(x.retries.toString(), { v -> b = x.copy(retries = v.toIntOrNull()?.coerceIn(0, 5) ?: 0) }, singleLine = true,
                            label = { Text(stringResource(R.string.wf_retries)) })
                        Row(Modifier.toggleable(x.continueOnError, role = Role.Checkbox) { b = x.copy(continueOnError = it) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(x.continueOnError, null); Text(stringResource(R.string.wf_continue_on_error), Modifier.padding(start = 8.dp))
                        }
                    }
                    is Block.If -> {
                        ConditionEditor(x.condition) { b = x.copy(condition = it) }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.wf_else_if_count, x.elseIfs.size), Modifier.weight(1f))
                            TextButton(onClick = { b = x.copy(elseIfs = x.elseIfs + Block.ElseIf(Condition.Compare("", Condition.Op.EQUALS, ""), emptyList())) }) { Text(stringResource(R.string.wf_add_else_if)) }
                        }
                        x.elseIfs.forEachIndexed { n, e ->
                            Text(stringResource(R.string.wf_else_if), style = MaterialTheme.typography.labelLarge)
                            ConditionEditor(e.condition) { c -> b = x.copy(elseIfs = x.elseIfs.mapIndexed { k, v -> if (k == n) v.copy(condition = c) else v }) }
                            TextButton(onClick = { b = x.copy(elseIfs = x.elseIfs.filterIndexed { k, _ -> k != n }) }) { Text(stringResource(R.string.writer_remove)) }
                        }
                    }
                    is Block.While -> ConditionEditor(x.condition) { b = x.copy(condition = it) }
                    is Block.Repeat -> {
                        OutlinedTextField(x.times, { b = x.copy(times = it) }, singleLine = true, label = { Text(stringResource(R.string.wf_times)) })
                        OutlinedTextField(x.indexVariable.orEmpty(), { b = x.copy(indexVariable = it.ifBlank { null }) }, singleLine = true, label = { Text(stringResource(R.string.wf_index_var)) })
                    }
                    is Block.Delay -> OutlinedTextField(x.millis, { b = x.copy(millis = it) }, singleLine = true, label = { Text(stringResource(R.string.wf_millis)) })
                    is Block.SetVariable -> {
                        OutlinedTextField(x.name, { b = x.copy(name = it.uppercase()) }, singleLine = true, label = { Text(stringResource(R.string.wf_var_name)) })
                        OutlinedTextField(x.value, { b = x.copy(value = it) }, label = { Text(stringResource(R.string.wf_var_value)) })
                    }
                    else -> Text(stringResource(R.string.wf_no_settings))
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(b) }) { Text(stringResource(R.string.template_save_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConditionEditor(condition: Condition, onChange: (Condition) -> Unit) {
    val ui = ConditionUi.from(condition)
    if (ui == null) {
        Text(stringResource(R.string.wf_condition_complex, ConditionUi.describe(condition)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    fun emit(u: ConditionUi) = onChange(u.toCondition())
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(ui.all, { emit(ui.copy(all = true)) }, { Text(stringResource(R.string.wf_match_all)) })
            FilterChip(!ui.all, { emit(ui.copy(all = false)) }, { Text(stringResource(R.string.wf_match_any)) })
            FilterChip(ui.negate, { emit(ui.copy(negate = !ui.negate)) }, { Text(stringResource(R.string.wf_not)) })
        }
        ui.items.forEachIndexed { i, c ->
            OutlinedTextField(c.left, { v -> emit(ui.copy(items = ui.items.mapIndexed { k, x -> if (k == i) x.copy(left = v) else x })) }, singleLine = true,
                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wf_left)) }, placeholder = { Text("\${BATTERY_LEVEL}") })
            var open by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { open = true }) { Text(ConditionUi.opSymbol(c.op)) }
                DropdownMenu(open, { open = false }) {
                    Condition.Op.entries.forEach { op ->
                        DropdownMenuItem(text = { Text(ConditionUi.opSymbol(op)) }, onClick = {
                            open = false; emit(ui.copy(items = ui.items.mapIndexed { k, x -> if (k == i) x.copy(op = op) else x }))
                        })
                    }
                }
                if (ui.items.size > 1) IconButton(onClick = { emit(ui.copy(items = ui.items.filterIndexed { k, _ -> k != i })) }) { Icon(Icons.Filled.Close, stringResource(R.string.writer_remove)) }
            }
            if (c.op != Condition.Op.IS_EMPTY && c.op != Condition.Op.IS_NOT_EMPTY) {
                OutlinedTextField(c.right, { v -> emit(ui.copy(items = ui.items.mapIndexed { k, x -> if (k == i) x.copy(right = v) else x })) }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.wf_right)) })
            }
        }
        TextButton(onClick = { emit(ui.copy(items = ui.items + Condition.Compare("", Condition.Op.EQUALS, ""))) }) { Text(stringResource(R.string.wf_add_comparison)) }
    }
}
