package dev.nfcstudio.app.feature.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.common.groupHexPairs
import dev.nfcstudio.app.data.database.ContentKind
import dev.nfcstudio.app.data.database.ScanEntryEntity
import dev.nfcstudio.app.data.repository.HistoryFilter
import dev.nfcstudio.app.feature.writer.Confirm
import dev.nfcstudio.app.ui.components.BackupMessage
import dev.nfcstudio.app.ui.components.DetailRow
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.RecordRow
import java.text.DateFormat
import java.util.Date

/** Local scan history with search, filters, favourites, delete and export (spec 4.5). */
@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val container = appContainer()
    val vm: HistoryViewModel = viewModel(factory = viewModelFactory { initializer { container.historyViewModel() } })
    val entries by vm.entries.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val technologies by vm.technologies.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    var expanded by rememberSaveable { mutableStateOf<Long?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::export)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.nav_history),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        message?.let { m -> item { BackupMessage(m, vm::dismissMessage) } }
        item {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { q -> vm.updateFilter { it.copy(query = q) } },
                label = { Text(stringResource(R.string.history_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { Filters(filter, technologies, vm::updateFilter) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { exportLauncher.launch("nfc-studio-history.json") },
                    enabled = !entries.isNullOrEmpty(),
                ) { Text(stringResource(R.string.history_export)) }
                OutlinedButton(onClick = { confirmClear = true }, enabled = !entries.isNullOrEmpty()) {
                    Text(stringResource(R.string.history_clear))
                }
            }
        }

        val list = entries
        when {
            list == null -> Unit
            list.isEmpty() -> item {
                MessageCard(
                    title = stringResource(R.string.history_empty),
                    body = stringResource(R.string.history_empty_detail),
                )
            }

            else -> items(list, key = { it.id }) { entry ->
                HistoryItem(
                    entry = entry,
                    expanded = expanded == entry.id,
                    onToggleExpanded = { expanded = if (expanded == entry.id) null else entry.id },
                    onFavourite = { vm.toggleFavourite(entry) },
                    onDelete = { pendingDelete = entry.id },
                )
            }
        }
    }

    if (confirmClear) {
        Confirm(
            title = stringResource(R.string.history_clear_title),
            body = stringResource(R.string.history_clear_detail),
            confirm = stringResource(R.string.history_clear),
            destructive = true,
            onConfirm = { confirmClear = false; vm.clearAll() },
            onDismiss = { confirmClear = false },
        )
    }
    pendingDelete?.let { id ->
        Confirm(
            title = stringResource(R.string.history_delete_title),
            body = stringResource(R.string.history_delete_detail),
            confirm = stringResource(R.string.history_delete),
            destructive = true,
            onConfirm = { pendingDelete = null; vm.delete(id) },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun Filters(filter: HistoryFilter, technologies: List<String>, update: ((HistoryFilter) -> HistoryFilter) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                FilterChip(
                    selected = filter.favouritesOnly,
                    onClick = { update { it.copy(favouritesOnly = !it.favouritesOnly) } },
                    label = { Text(stringResource(R.string.history_favourites)) },
                )
            }
            items(HistoryFilter.ResultFilter.entries.drop(1)) { r ->
                FilterChip(
                    selected = filter.result == r,
                    onClick = { update { it.copy(result = if (it.result == r) HistoryFilter.ResultFilter.ALL else r) } },
                    label = {
                        Text(stringResource(if (r == HistoryFilter.ResultFilter.SUCCESS) R.string.history_success else R.string.history_failed))
                    },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(HistoryFilter.Period.entries) { p ->
                FilterChip(
                    selected = filter.period == p,
                    onClick = { update { it.copy(period = p) } },
                    label = {
                        Text(
                            stringResource(
                                when (p) {
                                    HistoryFilter.Period.ALL -> R.string.period_all
                                    HistoryFilter.Period.DAY -> R.string.period_day
                                    HistoryFilter.Period.WEEK -> R.string.period_week
                                    HistoryFilter.Period.MONTH -> R.string.period_month
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (technologies.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(technologies) { tech ->
                    FilterChip(
                        selected = filter.technology == tech,
                        onClick = { update { it.copy(technology = if (it.technology == tech) null else tech) } },
                        label = { Text(tech) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(
    entry: ScanEntryEntity,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFavourite: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggleExpanded)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(entryTitle(entry), style = MaterialTheme.typography.titleSmall, maxLines = if (expanded) 10 else 2)
                    Text(
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt)) +
                            (entry.technologies.takeIf { it.isNotBlank() }?.let { " · " + it.replace(",", ", ") } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onFavourite) {
                    Icon(
                        if (entry.favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = stringResource(if (entry.favourite) R.string.history_unfavourite else R.string.history_favourite),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
                }
            }
            if (expanded) Details(entry)
        }
    }
}

@Composable
private fun Details(entry: ScanEntryEntity) {
    entry.ndefType?.let { DetailRow(stringResource(R.string.tag_ndef_type), it) }
    entry.uidHex?.let { DetailRow(stringResource(R.string.tag_uid), it.groupHexPairs(), copyable = true) }
    val records = HistoryViewModel.decodePayload(entry.payloadHex)
    when {
        records != null -> records.forEachIndexed { i, r -> RecordRow(index = i + 1, record = r) }
        entry.contentKind == ContentKind.RECORDS.name -> Text(
            stringResource(R.string.history_payload_not_stored),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun entryTitle(entry: ScanEntryEntity): String = when (runCatching { ContentKind.valueOf(entry.contentKind) }.getOrNull()) {
    ContentKind.RECORDS -> entry.summary.ifBlank { stringResource(R.string.history_kind_records) }
    ContentKind.EMPTY -> stringResource(R.string.history_kind_empty)
    ContentKind.MALFORMED -> stringResource(R.string.history_kind_malformed)
    ContentKind.UNREADABLE -> stringResource(R.string.history_kind_unreadable)
    ContentKind.NO_NDEF -> stringResource(R.string.history_kind_no_ndef)
    ContentKind.ERROR, null -> stringResource(R.string.history_kind_error, entry.result)
}
