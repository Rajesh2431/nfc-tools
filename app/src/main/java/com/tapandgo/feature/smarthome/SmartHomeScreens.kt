package com.tapandgo.feature.smarthome

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.smarthome.ConnectionDraft
import com.tapandgo.smarthome.ConnectionProblem
import com.tapandgo.smarthome.SmartConnection
import com.tapandgo.smarthome.SmartKind
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone

private fun kindIcon(k: SmartKind): ImageVector = when (k) {
    SmartKind.HOME_ASSISTANT -> Icons.Filled.Home
    SmartKind.MQTT -> Icons.Filled.Hub
    SmartKind.HUE -> Icons.Filled.Lightbulb
    else -> Icons.Filled.Power
}

@Composable
fun kindLabel(k: SmartKind): String = stringResource(
    when (k) {
        SmartKind.HOME_ASSISTANT -> R.string.sh_kind_ha
        SmartKind.MQTT -> R.string.sh_kind_mqtt
        SmartKind.HUE -> R.string.sh_kind_hue
        SmartKind.SHELLY_GEN1 -> R.string.sh_kind_shelly1
        SmartKind.SHELLY_GEN2 -> R.string.sh_kind_shelly2
        SmartKind.TASMOTA -> R.string.sh_kind_tasmota
        SmartKind.WLED -> R.string.sh_kind_wled
    },
)

@Composable
fun SmartHomeScreen(onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val vm: SmartHomeViewModel = hiltViewModel()
    val list by vm.connections.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ConnectionDraft?>(null) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text(stringResource(R.string.sh_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Button(onClick = { vm.clearProblem(); editing = ConnectionDraft() }) {
                Icon(Icons.Filled.Add, null); Text(stringResource(R.string.sh_add), Modifier.padding(start = 6.dp))
            }
        }
        message?.let { (text, err) ->
            item { MessageCard(text, tone = if (err) Tone.ERROR else Tone.SUCCESS) { TextButton(onClick = vm::dismiss) { Text(stringResource(R.string.writer_done)) } } }
        }
        if (list?.isEmpty() == true) item { MessageCard(stringResource(R.string.sh_empty), body = stringResource(R.string.sh_empty_detail)) }
        items(list.orEmpty(), key = { it.id }) { c ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(kindIcon(c.kind))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(c.name, style = MaterialTheme.typography.titleSmall)
                        Text("${kindLabel(c.kind)} · ${c.address}" + if (c.kind != SmartKind.HOME_ASSISTANT) ":${c.port}" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (testing == c.id) CircularProgressIndicator(Modifier.padding(8.dp))
                }
                Row(Modifier.padding(start = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpen(c.id) }) { Text(stringResource(R.string.sh_devices)) }
                    TextButton(onClick = { vm.test(c) }, enabled = testing == null) { Text(stringResource(R.string.sh_test)) }
                    IconButton(onClick = { vm.clearProblem(); editing = ConnectionDraft(c.id, c.kind, c.name, c.address, c.port, c.useTls, c.username, null) }) { Icon(Icons.Filled.Edit, stringResource(R.string.tasks_edit, c.name)) }
                    IconButton(onClick = { deleting = c.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.template_delete, c.name)) }
                }
            }
        }
        item { Text(stringResource(R.string.sh_security), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    editing?.let { d -> ConnectionDialog(d, vm, onDismiss = { editing = null }) }
    deleting?.let { id ->
        Confirm(stringResource(R.string.sh_delete_title), stringResource(R.string.sh_delete_detail), stringResource(R.string.history_delete),
            onConfirm = { deleting = null; vm.delete(id) }, onDismiss = { deleting = null }, destructive = true)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionDialog(initial: ConnectionDraft, vm: SmartHomeViewModel, onDismiss: () -> Unit) {
    var d by remember { mutableStateOf(initial) }
    var secret by remember { mutableStateOf("") }
    val problem by vm.problem.collectAsStateWithLifecycle()
    val isNew = initial.id == 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (isNew) R.string.sh_add else R.string.sh_edit)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isNew) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmartKind.entries.forEach { k ->
                        FilterChip(selected = d.kind == k, onClick = { d = d.copy(kind = k, port = k.defaultPort, useTls = false) }, label = { Text(kindLabel(k)) })
                    }
                }
                OutlinedTextField(d.name, { d = d.copy(name = it.take(40)) }, label = { Text(stringResource(R.string.template_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    d.address, { d = d.copy(address = it.take(300)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (d.kind == SmartKind.HOME_ASSISTANT) R.string.sh_url else R.string.sh_host)) },
                    placeholder = { Text(if (d.kind == SmartKind.HOME_ASSISTANT) "http://192.168.1.10:8123" else "192.168.1.20") },
                )
                if (d.kind != SmartKind.HOME_ASSISTANT) {
                    OutlinedTextField(d.port.toString(), { v -> v.filter(Char::isDigit).take(5).toIntOrNull()?.let { d = d.copy(port = it) } }, label = { Text(stringResource(R.string.sh_port)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (d.kind != SmartKind.HUE) Row(Modifier.fillMaxWidth().toggleable(d.useTls, role = Role.Switch) { d = d.copy(useTls = it, port = if (d.kind == SmartKind.MQTT) (if (it) 8883 else 1883) else (if (it) 443 else 80)) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.sh_tls), Modifier.weight(1f)); Switch(d.useTls, null)
                    }
                }
                if (d.kind != SmartKind.HOME_ASSISTANT && d.kind != SmartKind.HUE) {
                    OutlinedTextField(d.username, { d = d.copy(username = it.take(100)) }, label = { Text(stringResource(R.string.sh_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (d.kind != SmartKind.HUE) {
                    OutlinedTextField(
                        secret, { secret = it.take(4000) }, singleLine = true, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                        label = { Text(stringResource(if (d.kind == SmartKind.HOME_ASSISTANT) R.string.sh_token else R.string.sh_password)) },
                        supportingText = { Text(stringResource(if (isNew) R.string.sh_secret_note else R.string.sh_secret_keep)) },
                    )
                } else {
                    Text(stringResource(R.string.sh_hue_pair_note), style = MaterialTheme.typography.bodySmall)
                }
                problem?.let { Text(stringResource(problemRes(it)), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Hue: placeholder so validation passes; the real key comes from pairing.
                val s = when {
                    d.kind == SmartKind.HUE -> if (isNew) "" else null
                    secret.isEmpty() -> if (isNew) "" else null
                    else -> secret
                }
                vm.save(d.copy(secret = s), onDismiss)
            }) { Text(stringResource(R.string.template_save_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.writer_cancel)) } },
    )
}

private fun problemRes(p: ConnectionProblem) = when (p) {
    ConnectionProblem.NAME -> R.string.sh_problem_name
    ConnectionProblem.ADDRESS -> R.string.sh_problem_address
    ConnectionProblem.PORT -> R.string.sh_problem_port
    ConnectionProblem.PLAIN_REMOTE -> R.string.sh_problem_plain
    ConnectionProblem.SECRET_REQUIRED -> R.string.sh_problem_secret
    ConnectionProblem.DUPLICATE -> R.string.sh_problem_duplicate
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartDevicesScreen(modifier: Modifier = Modifier) {
    val vm: SmartDevicesViewModel = hiltViewModel()
    val c by vm.connection.collectAsStateWithLifecycle()
    val entities by vm.entities.collectAsStateWithLifecycle()
    val lights by vm.lights.collectAsStateWithLifecycle()
    val scenes by vm.scenes.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val conn: SmartConnection = c ?: run { CircularProgressIndicator(); return@Column }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(conn.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.padding(4.dp)) else TextButton(onClick = vm::refresh) { Text(stringResource(R.string.sh_refresh)) }
        }
        status?.let { (t, err) -> MessageCard(t, tone = if (err) Tone.ERROR else Tone.SUCCESS) }
        when (conn.kind) {
            SmartKind.HOME_ASSISTANT -> {
                OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.sh_filter)) })
                val shown = entities.filter { filter.isBlank() || it.entityId.contains(filter, true) || it.friendlyName.contains(filter, true) }.take(200)
                shown.forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.friendlyName, style = MaterialTheme.typography.bodyLarge)
                            Text("${e.entityId} · ${e.state}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (e.toggleable) Switch(checked = e.state in setOf("on", "open", "unlocked", "playing", "heat", "cool", "auto"), onCheckedChange = { vm.toggle(e) }, enabled = !busy)
                    }
                }
                Text(stringResource(R.string.sh_ha_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SmartKind.HUE -> {
                if (!conn.hasSecret) {
                    MessageCard(stringResource(R.string.sh_hue_pair_title), body = stringResource(R.string.sh_hue_pair_note)) {
                        Button(onClick = vm::pair, enabled = !busy) { Text(stringResource(R.string.sh_hue_pair)) }
                    }
                } else {
                    SectionCard(stringResource(R.string.sh_lights)) {
                        lights.forEach { l ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${l.name} (#${l.id})" + if (!l.reachable) " · " + stringResource(R.string.sh_unreachable) else "", Modifier.weight(1f))
                                Switch(l.on, { vm.hue(l.id, it) }, enabled = !busy && l.reachable)
                            }
                        }
                    }
                    if (scenes.isNotEmpty()) SectionCard(stringResource(R.string.sh_scenes)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            scenes.take(60).forEach { s -> AssistChip(onClick = { vm.scene(s) }, enabled = !busy, label = { Text(s.name) }) }
                        }
                    }
                }
            }
            SmartKind.MQTT -> {
                var topic by rememberSaveable { mutableStateOf("") }
                var payload by rememberSaveable { mutableStateOf("") }
                var retain by rememberSaveable { mutableStateOf(false) }
                var qos by rememberSaveable { mutableStateOf(0) }
                OutlinedTextField(topic, { topic = it.take(500) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.p_topic)) })
                OutlinedTextField(payload, { payload = it.take(10_000) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.p_payload)) })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(qos == 0, { qos = 0 }, { Text("QoS 0") }); FilterChip(qos == 1, { qos = 1 }, { Text("QoS 1") }, Modifier.padding(start = 6.dp))
                    Row(Modifier.padding(start = 12.dp).toggleable(retain, role = Role.Switch) { retain = it }, verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.p_retain)); Switch(retain, null) }
                }
                Button(onClick = { vm.publish(topic, payload, qos, retain) }, enabled = topic.isNotBlank() && !busy) { Text(stringResource(R.string.sh_publish)) }
            }
            else -> {
                var bri by rememberSaveable { mutableStateOf("") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.device(true) }, enabled = !busy) { Text("On") }
                    OutlinedButton(onClick = { vm.device(false) }, enabled = !busy) { Text("Off") }
                    OutlinedButton(onClick = { vm.device(null) }, enabled = !busy) { Text("Toggle") }
                }
                if (conn.kind == SmartKind.WLED || conn.kind == SmartKind.TASMOTA) Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(bri, { bri = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.p_percent)) })
                    TextButton(onClick = { bri.toIntOrNull()?.takeIf { it in 0..100 }?.let { vm.device(true, 0, it) } }, enabled = !busy) { Text(stringResource(R.string.lab_set)) }
                }
            }
        }
        Text(stringResource(R.string.sh_use_in_workflows, conn.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
