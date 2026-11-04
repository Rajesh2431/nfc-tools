package com.tapandgo.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ElevatedAssistChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.core.nfc.NfcState
import com.tapandgo.core.nfc.rememberCurrentNfcState
import com.tapandgo.feature.history.entryTitle
import com.tapandgo.feature.tasks.outcomeLabel
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.InfoRow
import com.tapandgo.ui.components.NfcStatusCard
import com.tapandgo.ui.components.SectionCard
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/** Navigation targets the dashboard can open. */
data class HomeActions(
    val read: () -> Unit,
    val write: () -> Unit,
    val erase: () -> Unit,
    val copy: () -> Unit,
    val createTask: () -> Unit,
    val tasks: () -> Unit,
    val runProfile: (Long) -> Unit,
    val history: () -> Unit,
    val tools: () -> Unit,
)

@Composable
fun HomeScreen(actions: HomeActions, modifier: Modifier = Modifier) {
    val vm: HomeViewModel = hiltViewModel()
    val layout by vm.layout.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Greeting(editing = editing, onToggleEdit = { editing = !editing })
        val cards = if (editing) layout.normalized() else layout.visible
        cards.forEach { card ->
            if (editing) {
                EditRow(card, layout, vm)
            } else {
                when (card) {
                    DashboardCard.STATUS -> StatusCard()
                    DashboardCard.QUICK_ACTIONS -> QuickActions(actions)
                    DashboardCard.RECENT_SCANS -> RecentScans(vm, actions)
                    DashboardCard.RECENT_RUNS -> RecentRuns(vm)
                    DashboardCard.PROFILES -> Profiles(vm, actions)
                    DashboardCard.FAVORITES -> Favourites(vm, actions)
                    DashboardCard.DEVICE_INFO -> DeviceInfo(vm.device)
                }
            }
        }
    }
}

@Composable
private fun Greeting(editing: Boolean, onToggleEdit: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))) {
            Image(painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.size(56.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(56.dp))
        }
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(
                stringResource(
                    when (hour) {
                        in 5..11 -> R.string.home_morning
                        in 12..17 -> R.string.home_afternoon
                        else -> R.string.home_evening
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onToggleEdit) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Text(stringResource(if (editing) R.string.writer_done else R.string.home_customize), Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun cardTitle(card: DashboardCard): String = stringResource(
    when (card) {
        DashboardCard.STATUS -> R.string.home_card_status
        DashboardCard.QUICK_ACTIONS -> R.string.home_card_quick
        DashboardCard.RECENT_SCANS -> R.string.home_card_recent
        DashboardCard.RECENT_RUNS -> R.string.home_card_runs
        DashboardCard.PROFILES -> R.string.home_card_profiles
        DashboardCard.FAVORITES -> R.string.home_card_favourites
        DashboardCard.DEVICE_INFO -> R.string.home_card_device
    },
)

@Composable
private fun EditRow(card: DashboardCard, layout: DashboardLayout, vm: HomeViewModel) {
    val title = cardTitle(card)
    SectionCard(title) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.editLayout { it.move(card, -1) } }) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.writer_move_up)) }
            IconButton(onClick = { vm.editLayout { it.move(card, 1) } }) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.writer_move_down)) }
            IconButton(onClick = { vm.editLayout { it.togglePinned(card) } }) {
                Icon(
                    Icons.Filled.PushPin,
                    stringResource(if (card in layout.pinned) R.string.home_unpin else R.string.home_pin, title),
                    tint = if (card in layout.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            IconButton(onClick = { vm.editLayout { it.toggleHidden(card) } }) {
                Icon(
                    if (card in layout.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    stringResource(if (card in layout.hidden) R.string.home_show_card else R.string.home_hide_card, title),
                )
            }
        }
    }
}

@Composable
private fun StatusCard() {
    val state by rememberCurrentNfcState()
    NfcStatusCard(nfcState = state)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(a: HomeActions) {
    SectionCard(stringResource(R.string.home_card_quick)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Quick(Icons.Filled.Nfc, R.string.nav_read, a.read)
            Quick(Icons.AutoMirrored.Filled.Send, R.string.nav_write, a.write)
            Quick(Icons.Filled.DeleteSweep, R.string.op_erase, a.erase)
            Quick(Icons.Filled.ContentCopy, R.string.op_copy, a.copy)
            Quick(Icons.Filled.AddTask, R.string.tasks_new, a.createTask)
            Quick(Icons.Filled.PlayArrow, R.string.home_run_task, a.tasks)
            Quick(Icons.Filled.History, R.string.home_saved_tags, a.history)
            Quick(Icons.Filled.Build, R.string.nav_tools, a.tools)
        }
    }
}

@Composable
private fun Quick(icon: ImageVector, label: Int, onClick: () -> Unit) {
    ElevatedAssistChip(onClick = onClick, label = { Text(stringResource(label)) }, leadingIcon = { Icon(icon, contentDescription = null) })
}

@Composable
private fun RecentScans(vm: HomeViewModel, a: HomeActions) {
    val list by vm.recentScans.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.home_card_recent)) {
        when {
            list == null -> Unit
            list!!.isEmpty() -> Text(stringResource(R.string.home_first_use), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> list!!.forEach { e -> ScanLine(e, a.history) }
        }
    }
}

@Composable
private fun ScanLine(e: com.tapandgo.data.database.ScanEntryEntity, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(if (e.favourite) Icons.Filled.Star else Icons.Filled.Nfc, size = 36.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(entryTitle(e), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(e.createdAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecentRuns(vm: HomeViewModel) {
    val runs by vm.recentRuns.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.home_card_runs)) {
        if (runs.isEmpty()) Text(stringResource(R.string.tasks_log_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        runs.forEach { r ->
            Text(
                "${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(r.createdAt))} · ${r.profileName} · " + stringResource(outcomeLabel(r.outcome)),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun Profiles(vm: HomeViewModel, a: HomeActions) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.home_card_profiles)) {
        if (profiles.isEmpty()) {
            Text(stringResource(R.string.tasks_empty_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        profiles.take(5).forEach { p ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = p.profile?.enabled == true, role = Role.Button) { a.runProfile(p.id) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Filled.Bolt, size = 36.dp, container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(p.name, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                if (p.profile?.enabled == true) Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.tasks_run_named, p.name))
            }
        }
    }
}

@Composable
private fun Favourites(vm: HomeViewModel, a: HomeActions) {
    val favs by vm.favourites.collectAsStateWithLifecycle()
    SectionCard(stringResource(R.string.home_card_favourites)) {
        if (favs.isEmpty()) Text(stringResource(R.string.home_no_favourites), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        favs.forEach { ScanLine(it, a.history) }
    }
}

@Composable
private fun DeviceInfo(d: DeviceFacts) {
    val yes = stringResource(R.string.value_yes)
    val no = stringResource(R.string.value_no)
    SectionCard(stringResource(R.string.home_card_device)) {
        InfoRow(Icons.Filled.PhoneAndroid, stringResource(R.string.home_device_model), d.model)
        InfoRow(Icons.Filled.PhoneAndroid, stringResource(R.string.home_device_android), "${d.android} (API ${d.sdk})")
        InfoRow(Icons.Filled.Nfc, stringResource(R.string.home_device_nfc), if (d.nfcFeature) yes else no)
        InfoRow(Icons.Filled.Nfc, stringResource(R.string.home_device_hce), if (d.hceFeature) yes else no)
        Text(stringResource(R.string.home_device_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(400.dp))
    }
}
