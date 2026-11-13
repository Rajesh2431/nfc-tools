package com.tapandgo.feature.root

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tapandgo.R
import com.tapandgo.data.settings.SettingsRepository
import com.tapandgo.root.CustomCommandPolicy
import com.tapandgo.root.DeviceDiagnostics
import com.tapandgo.root.RootAccess
import com.tapandgo.root.RootCommand
import com.tapandgo.root.RootExecutionService
import com.tapandgo.root.RootHistoryEntry
import com.tapandgo.root.RootProfile
import com.tapandgo.root.RootStatus
import com.tapandgo.root.ShellResult
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

/** Result shown after a command: title + output (already redacted by the service). */
data class RootOutput(val title: String, val result: ShellResult)

@HiltViewModel
class RootManagerViewModel @Inject constructor(
    private val root: RootExecutionService,
    private val settings: SettingsRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val status: StateFlow<RootStatus> = root.status
    val history: StateFlow<List<RootHistoryEntry>> = root.history
    val rootWorkflows: StateFlow<Boolean> = settings.settings.map { it.rootWorkflowActions }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _output = MutableStateFlow<RootOutput?>(null)
    val output: StateFlow<RootOutput?> = _output.asStateFlow()
    val diagnostics: List<DeviceDiagnostics.Item> by lazy { DeviceDiagnostics.collect(context) }

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch { try { block() } finally { _busy.value = false } }
    }

    fun checkAccess() = work { root.checkAccess() }

    fun run(cmd: RootCommand) = work { _output.value = RootOutput(cmd.label, root.execute(cmd)) }

    fun runProfile(p: RootProfile) = work {
        val lines = StringBuilder()
        var allOk = true
        for (step in p.steps) {
            val r = root.execute(step)
            allOk = allOk && r.ok
            lines.append("${step.label}: ").append(
                when {
                    r.error != null -> r.error
                    r.timedOut -> "timeout"
                    r.ok -> "OK"
                    else -> "exit ${r.exitCode} ${r.stderr.trim().take(120)}"
                },
            ).append('\n')
        }
        _output.value = RootOutput(p.name, ShellResult(if (allOk) 0 else 1, lines.toString().trim(), ""))
    }

    fun runCustom(script: String) = work { _output.value = RootOutput("Custom", root.executeCustom(script)) }
    fun dismissOutput() { _output.value = null }
    fun clearHistory() = root.clearHistory()
    fun setRootWorkflows(on: Boolean) = viewModelScope.launch { settings.update { it.copy(rootWorkflowActions = on) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RootManagerScreen(modifier: Modifier = Modifier) {
    val vm: RootManagerViewModel = hiltViewModel()
    val status by vm.status.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val output by vm.output.collectAsStateWithLifecycle()
    val rootWorkflows by vm.rootWorkflows.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val granted = status.access == RootAccess.GRANTED
    val ctx = androidx.compose.ui.platform.LocalResources.current

    fun guarded(cmd: RootCommand) {
        if (cmd.destructive) confirm = ctx.getString(R.string.root_confirm_cmd, cmd.label) to { vm.run(cmd) } else vm.run(cmd)
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.root_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        MessageCard(
            stringResource(
                when (status.access) {
                    RootAccess.GRANTED -> R.string.root_granted
                    RootAccess.DENIED -> R.string.root_denied
                    RootAccess.NOT_ROOTED -> R.string.root_unavailable
                    RootAccess.UNKNOWN -> R.string.root_unknown
                },
            ),
            body = buildString {
                if (status.indicators.suPaths.isNotEmpty()) append(ctx.getString(R.string.root_su_found, status.indicators.suPaths.joinToString())).append('\n')
                if (status.indicators.managers.isNotEmpty()) append(ctx.getString(R.string.root_managers, status.indicators.managers.joinToString())).append('\n')
                if (!status.indicators.any) append(ctx.getString(R.string.root_no_indicators)).append('\n')
                if (granted) append(ctx.getString(R.string.root_impl, status.implementation.name, status.version ?: "?")).append('\n')
                status.selinux?.let { append("SELinux: ").append(it) }
            }.trim(),
            tone = when (status.access) {
                RootAccess.GRANTED -> Tone.SUCCESS
                RootAccess.DENIED, RootAccess.NOT_ROOTED -> Tone.ERROR
                RootAccess.UNKNOWN -> Tone.NEUTRAL
            },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::checkAccess, enabled = !busy) { Text(stringResource(R.string.root_check)) }
                if (busy) CircularProgressIndicator(Modifier.padding(4.dp))
            }
        }

        SectionCard(stringResource(R.string.root_diagnostics)) {
            vm.diagnostics.forEach { d ->
                Text(d.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(d.value, style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.root_diag_more), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RootCommand.Thermal, RootCommand.Processes, RootCommand.SelinuxStatus, RootCommand.Gpu).forEach { c ->
                    AssistChip(onClick = { vm.run(c) }, enabled = granted && !busy, label = { Text(c.label) })
                }
                AssistChip(onClick = { confirm = ctx.getString(R.string.root_confirm_logs) to { vm.run(RootCommand.RecentLogs) } }, enabled = granted && !busy, label = { Text(RootCommand.RecentLogs.label) })
            }
        }

        if (!granted) {
            MessageCard(stringResource(R.string.root_actions_locked), body = stringResource(R.string.root_actions_locked_detail))
        } else {
            SectionCard(stringResource(R.string.root_connectivity)) {
                OnOffRow("Airplane mode", busy, { guarded(RootCommand.Airplane(true)) }, { guarded(RootCommand.Airplane(false)) }, RootCommand.Airplane(true).supportedOnThisSdk())
                OnOffRow("Mobile data", busy, { guarded(RootCommand.MobileData(true)) }, { guarded(RootCommand.MobileData(false)) })
                OnOffRow("Wi-Fi", busy, { guarded(RootCommand.Wifi(true)) }, { guarded(RootCommand.Wifi(false)) })
                OnOffRow("Bluetooth", busy, { guarded(RootCommand.Bluetooth(true)) }, { guarded(RootCommand.Bluetooth(false)) })
                OutlinedButton(onClick = { guarded(RootCommand.NetworkReset) }, enabled = !busy && RootCommand.NetworkReset.supportedOnThisSdk()) { Text(stringResource(R.string.root_network_reset)) }
            }

            SectionCard(stringResource(R.string.root_display)) {
                var density by rememberSaveable { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(density, { density = it.filter(Char::isDigit).take(3) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.root_density)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    val dpi = density.toIntOrNull()?.takeIf { it in 120..640 }
                    TextButton(onClick = { dpi?.let { guarded(RootCommand.Density(it)) } }, enabled = dpi != null && !busy) { Text(stringResource(R.string.lab_set)) }
                    TextButton(onClick = { guarded(RootCommand.Density(null)) }, enabled = !busy) { Text(stringResource(R.string.root_reset)) }
                }
                var size by rememberSaveable { mutableStateOf("") }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(size, { size = it.filter { c -> c.isDigit() || c == 'x' }.take(9) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.root_resolution)) })
                    val parsed = size.split('x').takeIf { it.size == 2 }?.let { (w, h) -> w.toIntOrNull()?.let { a -> h.toIntOrNull()?.let { b -> a to b } } }
                        ?.takeIf { it.first in 320..4096 && it.second in 320..4096 }
                    TextButton(onClick = { parsed?.let { confirm = ctx.getString(R.string.root_confirm_resolution) to { vm.run(RootCommand.Resolution(it)) } } }, enabled = parsed != null && !busy) { Text(stringResource(R.string.lab_set)) }
                    TextButton(onClick = { guarded(RootCommand.Resolution(null)) }, enabled = !busy) { Text(stringResource(R.string.root_reset)) }
                }
                Text(stringResource(R.string.root_animation), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RootCommand.AnimationScale.Scale.entries.forEach { s ->
                        AssistChip(onClick = { guarded(RootCommand.AnimationScale(s)) }, enabled = !busy, label = { Text("${s.v}×") })
                    }
                }
                Text(stringResource(R.string.root_brightness), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 64, 128, 192, 255).forEach { b -> AssistChip(onClick = { guarded(RootCommand.Brightness(b)) }, enabled = !busy, label = { Text("${b * 100 / 255}%") }) }
                }
            }

            SectionCard(stringResource(R.string.root_profiles)) {
                RootProfile.entries.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                            Text(p.steps.joinToString(" · ") { it.label + if (!it.supportedOnThisSdk()) " (n/a)" else "" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { confirm = ctx.getString(R.string.root_confirm_profile, p.name) to { vm.runProfile(p) } }, enabled = !busy) { Text(stringResource(R.string.tasks_run)) }
                    }
                }
            }

            SectionCard(stringResource(R.string.root_power)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RootCommand.RebootTarget.entries.forEach { t -> OutlinedButton(onClick = { guarded(RootCommand.Reboot(t)) }, enabled = !busy) { Text("Reboot " + t.name.lowercase()) } }
                    OutlinedButton(onClick = { guarded(RootCommand.PowerOff) }, enabled = !busy) { Text(stringResource(R.string.root_power_off)) }
                    OutlinedButton(onClick = { guarded(RootCommand.RestartSystemUi) }, enabled = !busy) { Text(stringResource(R.string.root_restart_ui)) }
                }
            }

            SectionCard(stringResource(R.string.root_console)) {
                var script by rememberSaveable { mutableStateOf("") }
                Text(stringResource(R.string.root_console_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(script, { script = it.take(CustomCommandPolicy.MAX_LENGTH) }, Modifier.fillMaxWidth(), minLines = 2, label = { Text(stringResource(R.string.root_console_input)) }, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                Button(onClick = { confirm = ctx.getString(R.string.root_confirm_custom, script.take(300)) to { vm.runCustom(script) } }, enabled = CustomCommandPolicy.validate(script) && !busy) { Text(stringResource(R.string.tasks_run)) }
            }

            Row(Modifier.fillMaxWidth().toggleable(rootWorkflows, role = Role.Switch, onValueChange = vm::setRootWorkflows), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.root_workflows), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.root_workflows_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(rootWorkflows, null)
            }
        }

        SectionCard(stringResource(R.string.root_history)) {
            if (history.isEmpty()) Text(stringResource(R.string.wf_history_empty), style = MaterialTheme.typography.bodySmall)
            val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
            history.take(50).forEach { h ->
                Text("${fmt.format(Date(h.time))} · ${h.category} · ${h.label} · ${h.outcome}" + (h.exitCode?.let { " ($it)" } ?: ""), style = MaterialTheme.typography.bodySmall)
            }
            if (history.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text(stringResource(R.string.tasks_log_clear)) }
        }
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(stringResource(R.string.root_confirm_title)) },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text(stringResource(R.string.tasks_run)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.writer_cancel)) } },
        )
    }
    output?.let { o ->
        AlertDialog(
            onDismissRequest = vm::dismissOutput,
            title = { Text("${o.title} · " + when { o.result.error != null -> o.result.error; o.result.timedOut -> "timeout"; else -> "exit ${o.result.exitCode}" }) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    val body = (o.result.stdout + if (o.result.stderr.isNotBlank()) "\n--- stderr ---\n" + o.result.stderr else "").trim().take(20_000)
                    Text(body.ifEmpty { stringResource(R.string.root_no_output) }, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.horizontalScroll(rememberScrollState()))
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissOutput) { Text(stringResource(R.string.writer_done)) } },
        )
    }
}

@Composable
private fun OnOffRow(label: String, busy: Boolean, on: () -> Unit, off: () -> Unit, supported: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label + if (!supported) " (Android 11+)" else "", Modifier.weight(1f))
        TextButton(onClick = on, enabled = !busy && supported) { Text("On") }
        TextButton(onClick = off, enabled = !busy && supported) { Text("Off") }
    }
}
