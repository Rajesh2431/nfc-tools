package com.tapandgo.feature.library

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.data.library.LibrarySort
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import java.text.DateFormat
import java.util.Date

@Composable
fun LibraryScreen(onOpen: (Long) -> Unit, modifier: Modifier = Modifier) {
    val vm: LibraryViewModel = hiltViewModel()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val exported by vm.exported.collectAsStateWithLifecycle()
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { it?.let { u -> vm.export(u, TextExport.CSV) } }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let { u -> vm.export(u, TextExport.TXT) } }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                filter.query, { q -> vm.update { it.copy(query = q) } }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.library_search)) },
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip(filter.favouritesOnly, { vm.update { it.copy(favouritesOnly = !it.favouritesOnly) } }, { Text(stringResource(R.string.history_favourites)) }) }
                items(LibrarySort.entries) { s ->
                    FilterChip(filter.sort == s, { vm.update { it.copy(sort = s) } }, {
                        Text(stringResource(when (s) {
                            LibrarySort.UPDATED -> R.string.library_sort_updated
                            LibrarySort.NAME -> R.string.library_sort_name
                            LibrarySort.SCANNED -> R.string.library_sort_scanned
                        }))
                    })
                }
            }
        }
        if (folders.isNotEmpty()) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(folders) { f -> FilterChip(filter.folder == f, { vm.update { it.copy(folder = if (it.folder == f) null else f) } }, { Text(f) }) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { csv.launch("tapandgo-library.csv") }, enabled = !tags.isNullOrEmpty()) { Text(stringResource(R.string.library_export_csv)) }
                OutlinedButton(onClick = { txt.launch("tapandgo-library.txt") }, enabled = !tags.isNullOrEmpty()) { Text(stringResource(R.string.library_export_txt)) }
            }
            exported?.let { Text(stringResource(if (it) R.string.library_exported else R.string.backup_io_failed_detail), style = MaterialTheme.typography.bodySmall) }
        }
        val list = tags
        if (list != null && list.isEmpty()) {
            item { MessageCard(title = stringResource(R.string.library_empty), body = stringResource(R.string.library_empty_detail)) }
        }
        items(list.orEmpty(), key = { it.id }) { t ->
            Card(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(t.id) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(iconOf(t.icon))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(t.folder.takeIf { it.isNotBlank() }, t.labels.takeIf { it.isNotBlank() }?.replace(",", " · "),
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(t.updatedAt))).joinToString(" — "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { vm.toggleFavourite(t) }) {
                        Icon(if (t.favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            stringResource(if (t.favourite) R.string.history_unfavourite else R.string.history_favourite))
                    }
                }
            }
        }
    }
}
