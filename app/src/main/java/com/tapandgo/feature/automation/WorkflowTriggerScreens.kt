package com.tapandgo.feature.automation

import android.app.StatusBarManager
import android.content.ComponentName
import android.graphics.drawable.Icon as PlatformIcon
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.automation.LaunchIntents
import com.tapandgo.automation.RunSource
import com.tapandgo.automation.WorkflowLaunch
import com.tapandgo.automation.WorkflowTileService
import com.tapandgo.automation.WorkflowTriggers
import com.tapandgo.automation.engine.RunStatus
import com.tapandgo.ui.components.CopyButton
import com.tapandgo.billing.PremiumFeature
import com.tapandgo.feature.premium.PremiumGate
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone

/** Shown when a tag, shortcut, tile or other app asks to run a workflow. */
@Composable
fun WorkflowLaunchScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val vm: WorkflowLaunchViewModel = hiltViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (val s = state) {
            LaunchState.Loading -> CircularProgressIndicator()
            LaunchState.NotFound -> MessageCard(stringResource(R.string.wf_launch_not_found), body = stringResource(R.string.wf_launch_not_found_detail), tone = Tone.ERROR) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.writer_done)) }
            }
            is LaunchState.Blocked -> MessageCard(stringResource(R.string.wf_launch_blocked, s.name), body = stringResource(R.string.wf_launch_blocked_detail), tone = Tone.ERROR) {
                TextButton(onClick = onDone) { Text(stringResource(R.string.writer_done)) }
            }
            is LaunchState.Confirm -> MessageCard(
                stringResource(R.string.wf_launch_confirm, s.workflow.name),
                body = stringResource(if (s.source == RunSource.EXTERNAL) R.string.wf_launch_from_app else R.string.wf_launch_from_tag) +
                    "\n" + stringResource(R.string.wf_blocks, com.tapandgo.automation.engine.WorkflowEdit.count(s.workflow.workflow?.blocks.orEmpty())),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::confirm) { Text(stringResource(R.string.tasks_run)) }
                    OutlinedButton(onClick = { vm.cancel(); onDone() }) { Text(stringResource(R.string.writer_cancel)) }
                }
            }
            is LaunchState.Running -> MessageCard(stringResource(R.string.wf_launch_running, s.name), tone = Tone.WORKING)
            is LaunchState.Cancelled -> MessageCard(stringResource(R.string.wf_launch_cancelled, s.name))
            is LaunchState.Done -> {
                MessageCard(
                    "${s.name} · ${stringResource(statusRes(s.result.status))}",
                    body = s.result.error,
                    tone = if (s.result.status == RunStatus.SUCCESS) Tone.SUCCESS else Tone.ERROR,
                ) { TextButton(onClick = onDone) { Text(stringResource(R.string.writer_done)) } }
                if (s.result.log.isEmpty()) Text(stringResource(R.string.wf_no_actions_ran))
                s.result.log.forEach { st ->
                    Text("${st.path}  ${st.actionId}  ${st.status}" + (st.detail?.let { " — $it" } ?: ""), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun statusRes(s: RunStatus) = when (s) {
    RunStatus.SUCCESS -> R.string.wf_status_success
    RunStatus.STOPPED -> R.string.wf_status_stopped
    RunStatus.FAILED -> R.string.wf_status_failed
    RunStatus.TIMED_OUT -> R.string.wf_status_timeout
    RunStatus.LIMIT_EXCEEDED -> R.string.wf_status_limit
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkflowTriggersScreen(onWriteTag: () -> Unit, onOpenPremium: () -> Unit, modifier: Modifier = Modifier) {
    val vm: WorkflowTriggersViewModel = hiltViewModel()
    val wf by vm.workflow.collectAsStateWithLifecycle()
    val missing by vm.missing.collectAsStateWithLifecycle()
    val externalEnabled by vm.externalEnabled.collectAsStateWithLifecycle()
    val tileId by vm.tileWorkflowId.collectAsStateWithLifecycle()
    val onWidget by vm.onWidget.collectAsStateWithLifecycle()
    val widgetFull by vm.widgetFull.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    var notice by rememberSaveable { mutableStateOf<Int?>(null) }
    var revealToken by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (missing) { MessageCard(stringResource(R.string.wf_launch_not_found), tone = Tone.ERROR); return@Column }
        val w = wf ?: run { CircularProgressIndicator(); return@Column }
        val t = w.triggers
        Text(w.name, style = MaterialTheme.typography.titleLarge)
        if (w.workflow == null) MessageCard(stringResource(R.string.wf_invalid), tone = Tone.ERROR)
        notice?.let { MessageCard(stringResource(it)) }

        SectionCard(stringResource(R.string.trg_tag_title)) {
            Text(stringResource(R.string.trg_tag_detail), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { vm.writeTag(onWriteTag) }, enabled = w.workflow != null) { Text(stringResource(R.string.trg_write_tag)) }
            SwitchRow(stringResource(R.string.trg_auto_run), stringResource(R.string.trg_auto_run_detail), t.autoRun, vm::setAutoRun)
        }

        SectionCard(stringResource(R.string.trg_shortcut_title)) { PremiumGate(PremiumFeature.SHORTCUTS_AND_TILE, onOpenPremium) {
            Text(stringResource(R.string.trg_shortcut_detail), style = MaterialTheme.typography.bodySmall)
            val canPin = LaunchIntents.canPinShortcut(context)
            Button(onClick = {
                vm.withToken { token -> notice = if (LaunchIntents.requestPin(context, w.id, w.name, token)) R.string.trg_shortcut_requested else R.string.trg_shortcut_unsupported }
            }, enabled = canPin && w.workflow != null) { Text(stringResource(R.string.trg_add_shortcut)) }
            if (!canPin) Text(stringResource(R.string.trg_shortcut_unsupported), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            SwitchRow(stringResource(R.string.widget_show), stringResource(R.string.widget_show_detail), onWidget) { vm.setOnWidget(context, it) }
            if (widgetFull) Text(stringResource(R.string.widget_full), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } }

        SectionCard(stringResource(R.string.trg_tile_title)) { PremiumGate(PremiumFeature.SHORTCUTS_AND_TILE, onOpenPremium) {
            Text(stringResource(if (tileId == w.id) R.string.trg_tile_current else R.string.trg_tile_detail), style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::useForTile, enabled = tileId != w.id && w.workflow != null) { Text(stringResource(R.string.trg_use_tile)) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OutlinedButton(onClick = {
                        val sbm = context.getSystemService(StatusBarManager::class.java)
                        sbm.requestAddTileService(
                            ComponentName(context, WorkflowTileService::class.java),
                            res.getString(R.string.tile_label),
                            PlatformIcon.createWithResource(context, R.drawable.ic_launcher_monochrome),
                            context.mainExecutor,
                        ) { }
                    }) { Text(stringResource(R.string.trg_add_tile)) }
                }
            }
        } }

        SectionCard(stringResource(R.string.trg_schedule_title)) { PremiumGate(PremiumFeature.SCHEDULED_WORKFLOWS, onOpenPremium) {
            Text(stringResource(R.string.trg_schedule_detail), style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WorkflowTriggers.SCHEDULE_CHOICES.forEach { m ->
                    FilterChip(selected = t.scheduleMinutes == m, onClick = { vm.setSchedule(m) }, enabled = w.workflow != null, label = { Text(scheduleLabel(m)) })
                }
            }
        } }

        SectionCard(stringResource(R.string.ctx_title)) { PremiumGate(PremiumFeature.CONTEXT_TRIGGERS, onOpenPremium) {
            ContextTriggerSection(vm)
        } }

        SectionCard(stringResource(R.string.trg_external_title)) { PremiumGate(PremiumFeature.EXTERNAL_TRIGGERS, onOpenPremium) {
            Text(stringResource(R.string.trg_external_detail), style = MaterialTheme.typography.bodySmall)
            if (!externalEnabled) {
                Text(stringResource(R.string.trg_external_global_off), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.enableExternalGlobally() }) { Text(stringResource(R.string.trg_external_enable_global)) }
            }
            SwitchRow(stringResource(R.string.trg_external_allow), null, t.allowExternal, vm::setAllowExternal)
            if (t.allowExternal && t.token != null) {
                Text("action: ${WorkflowLaunch.ACTION_RUN}\nextra ${WorkflowLaunch.EXTRA_TOKEN}:", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (revealToken) t.token else "••••••••-••••", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { revealToken = !revealToken }) { Text(stringResource(if (revealToken) R.string.hide else R.string.reveal)) }
                    CopyButton(t.token, stringResource(R.string.trg_token))
                }
            }
        } }
    }
}

@Composable
private fun scheduleLabel(m: Int): String = when {
    m == 0 -> stringResource(R.string.trg_schedule_off)
    m < 60 -> stringResource(R.string.trg_every_min, m)
    else -> stringResource(R.string.trg_every_hours, m / 60)
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked, null)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContextTriggerSection(vm: WorkflowTriggersViewModel) {
    val list by vm.contextTriggers.collectAsStateWithLifecycle()
    val problem by vm.contextProblem.collectAsStateWithLifecycle()
    var refresh by remember { mutableStateOf(0) }
    val fine = remember(refresh) { vm.contextManager.hasFineLocation() }
    val background = remember(refresh) { vm.contextManager.hasBackgroundLocation() }
    val askFine = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val askBackground = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { refresh++ }
    var type by rememberSaveable { mutableStateOf(com.tapandgo.automation.ContextType.WIFI_CONNECT) }
    var ssid by rememberSaveable { mutableStateOf("") }
    var lat by rememberSaveable { mutableStateOf("") }
    var lng by rememberSaveable { mutableStateOf("") }
    var radius by rememberSaveable { mutableStateOf("200") }

    Text(stringResource(R.string.ctx_detail), style = MaterialTheme.typography.bodySmall)
    if (!fine) {
        Text(stringResource(R.string.ctx_need_location), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { askFine.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text(stringResource(R.string.ctx_allow_location)) }
    } else if (!background && Build.VERSION.SDK_INT >= 29) {
        Text(stringResource(R.string.ctx_need_background), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { askBackground.launch(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) { Text(stringResource(R.string.ctx_allow_background)) }
    }
    list.forEach { t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (t.type) {
                    "WIFI_CONNECT" -> stringResource(R.string.ctx_wifi_connect) + " “${t.ssid}”"
                    "WIFI_DISCONNECT" -> stringResource(R.string.ctx_wifi_disconnect) + " “${t.ssid}”"
                    "AREA_ENTER" -> stringResource(R.string.ctx_area_enter) + " %.5f, %.5f · %d m".format(t.latitude, t.longitude, t.radiusMeters)
                    else -> stringResource(R.string.ctx_area_exit) + " %.5f, %.5f · %d m".format(t.latitude, t.longitude, t.radiusMeters)
                },
                Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { vm.deleteContext(t.id) }) { Text(stringResource(R.string.writer_remove)) }
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.tapandgo.automation.ContextType.entries.forEach { c ->
            FilterChip(selected = type == c, onClick = { type = c }, label = {
                Text(stringResource(when (c) {
                    com.tapandgo.automation.ContextType.WIFI_CONNECT -> R.string.ctx_wifi_connect
                    com.tapandgo.automation.ContextType.WIFI_DISCONNECT -> R.string.ctx_wifi_disconnect
                    com.tapandgo.automation.ContextType.AREA_ENTER -> R.string.ctx_area_enter
                    com.tapandgo.automation.ContextType.AREA_EXIT -> R.string.ctx_area_exit
                }))
            })
        }
    }
    if (type.name.startsWith("WIFI")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(ssid, { ssid = it.take(32) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.ctx_ssid)) })
            TextButton(onClick = { vm.contextManager.currentSsid()?.let { ssid = it } }, enabled = fine) { Text(stringResource(R.string.ctx_current)) }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedTextField(lat, { lat = it.take(12) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.p_lat)) })
            androidx.compose.material3.OutlinedTextField(lng, { lng = it.take(12) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.p_lng)) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.OutlinedTextField(radius, { radius = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.ctx_radius)) })
            TextButton(onClick = { vm.contextManager.lastLocation()?.let { (a, b) -> lat = "%.6f".format(java.util.Locale.ROOT, a); lng = "%.6f".format(java.util.Locale.ROOT, b) } }, enabled = fine) { Text(stringResource(R.string.ctx_here)) }
        }
    }
    problem?.let {
        Text(stringResource(when (it) {
            com.tapandgo.automation.ContextRules.Problem.SSID -> R.string.ctx_problem_ssid
            com.tapandgo.automation.ContextRules.Problem.LOCATION -> R.string.ctx_problem_location
            com.tapandgo.automation.ContextRules.Problem.RADIUS -> R.string.ctx_problem_radius
        }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = { vm.addContext(type, ssid, lat.toDoubleOrNull(), lng.toDoubleOrNull(), radius.toIntOrNull()) }, enabled = fine) { Text(stringResource(R.string.ctx_add)) }
    Text(stringResource(R.string.ctx_limits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
