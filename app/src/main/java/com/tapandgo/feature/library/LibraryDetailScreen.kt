package com.tapandgo.feature.library

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.core.common.groupHexPairs
import com.tapandgo.data.library.Library
import com.tapandgo.data.library.LibraryMeta
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.ui.components.InfoRow
import com.tapandgo.ui.components.RecordRow
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.ndefTypeName

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryDetailScreen(onRestore: (Long) -> Unit, onDeleted: () -> Unit, modifier: Modifier = Modifier) {
    val vm: LibraryDetailViewModel = hiltViewModel()
    val tag by vm.tag.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    val differences by vm.differences.collectAsStateWithLifecycle()
    val photoFailed by vm.photoFailed.collectAsStateWithLifecycle()
    val t = tag ?: return
    var meta by remember(t.id) { mutableStateOf(Library.metaOf(t)) }
    LaunchedEffect(t.updatedAt) { meta = Library.metaOf(t) }
    var saved by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { it?.let(vm::attachPhoto) }
    val unknown = stringResource(R.string.value_unknown)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        vm.photo(t)?.let { file ->
            val bmp = remember(file.path, t.updatedAt) {
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
            }
            bmp?.let {
                Image(it, contentDescription = stringResource(R.string.library_photo_of, t.name), contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(24.dp)))
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onRestore(t.id) }, enabled = t.payloadHex != null) { Text(stringResource(R.string.library_restore)) }
            FilledTonalButton(onClick = vm::toggleFavourite) {
                Text(stringResource(if (t.favourite) R.string.history_unfavourite else R.string.history_favourite))
            }
            OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Text(stringResource(R.string.library_photo))
            }
            OutlinedButton(onClick = { picking = true }, enabled = others.size > 1) { Text(stringResource(R.string.library_compare)) }
            OutlinedButton(onClick = { confirmDelete = true }) { Text(stringResource(R.string.history_delete)) }
        }
        if (photoFailed) Text(stringResource(R.string.library_photo_failed), color = MaterialTheme.colorScheme.error)

        SectionCard(stringResource(R.string.library_details)) {
            MetaForm(meta, problem) { meta = it; saved = false }
            Button(onClick = { vm.save(meta) { saved = true } }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.template_save_confirm)) }
            if (saved) Text(stringResource(R.string.library_saved), style = MaterialTheme.typography.bodySmall)
        }

        SectionCard(stringResource(R.string.tag_details)) {
            InfoRow(Icons.Filled.Category, stringResource(R.string.tag_type), t.chipName ?: unknown)
            InfoRow(Icons.Filled.Layers, stringResource(R.string.tag_technologies), t.technologies.replace(",", ", "))
            InfoRow(Icons.Filled.Fingerprint, stringResource(R.string.tag_uid), t.uidHex?.groupHexPairs()?.replace(' ', ':') ?: stringResource(R.string.value_not_exposed))
            InfoRow(Icons.Filled.Description, stringResource(R.string.tag_data_format), ndefTypeName(t.ndefType) ?: unknown)
            InfoRow(Icons.Filled.DataUsage, stringResource(R.string.tag_size), t.capacityBytes?.let { stringResource(R.string.value_bytes, it) } ?: unknown)
        }
        val records = vm.records(t)
        SectionCard(stringResource(R.string.tag_records_title)) {
            if (records.isEmpty()) Text(stringResource(R.string.ndef_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            records.forEachIndexed { i, r -> if (i > 0) HorizontalDivider(); RecordRow(i + 1, r) }
        }
    }

    if (confirmDelete) {
        Confirm(stringResource(R.string.library_delete_title), stringResource(R.string.library_delete_detail), stringResource(R.string.history_delete),
            onConfirm = { confirmDelete = false; vm.delete(onDeleted) }, onDismiss = { confirmDelete = false }, destructive = true)
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.library_compare_with)) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(others.filter { it.id != t.id }, key = { it.id }) { o ->
                        Text(o.name, Modifier.fillMaxWidth().clickable(role = Role.Button) { picking = false; vm.compareWith(o) }.padding(vertical = 12.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
    differences?.let { (other, diffs) ->
        AlertDialog(
            onDismissRequest = vm::clearComparison,
            title = { Text(stringResource(R.string.library_compare_title, t.name, other.name)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (diffs.isEmpty()) Text(stringResource(R.string.library_compare_same))
                    diffs.forEach { d ->
                        Text(stringResource(diffLabel(d.field)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("◀ " + (d.left ?: "—"), style = MaterialTheme.typography.bodySmall)
                        Text("▶ " + (d.right ?: "—"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = vm::clearComparison) { Text(stringResource(R.string.writer_done)) } },
        )
    }
}

/** Save-to-library dialog used by the Read tab. */
@Composable
fun SaveToLibraryDialog(
    duplicates: List<String>?,
    problem: LibraryMeta.Problem?,
    onSave: (LibraryMeta, force: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var meta by remember { mutableStateOf(LibraryMeta(name = "")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_save)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.library_save_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                duplicates?.let { Text(stringResource(R.string.library_duplicates, it.joinToString(", ")), color = MaterialTheme.colorScheme.error) }
                MetaForm(meta, problem) { meta = it }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(meta, duplicates != null) }) {
                Text(stringResource(if (duplicates != null) R.string.library_save_anyway else R.string.template_save_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}
