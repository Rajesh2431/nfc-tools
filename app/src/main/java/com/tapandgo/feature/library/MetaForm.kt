package com.tapandgo.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tapandgo.R
import com.tapandgo.data.library.LibraryIcon
import com.tapandgo.data.library.LibraryMeta

/** Shared editor for library metadata (save dialog and detail screen). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetaForm(meta: LibraryMeta, problem: LibraryMeta.Problem?, onChange: (LibraryMeta) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            meta.name, { onChange(meta.copy(name = it)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.template_name) + " *") },
            isError = problem != null, supportingText = problem?.let { p -> { Text(stringResource(metaProblemText(p))) } },
        )
        OutlinedTextField(meta.description, { onChange(meta.copy(description = it)) }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.template_description)) })
        OutlinedTextField(meta.folder, { onChange(meta.copy(folder = it)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_folder)) })
        OutlinedTextField(
            meta.labels.joinToString(", "),
            { v -> onChange(meta.copy(labels = v.split(',').map { it.trim() }.filter { it.isNotEmpty() })) },
            singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_labels)) },
        )
        Text(stringResource(R.string.library_icon), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LibraryIcon.entries.forEach { i ->
                FilterChip(selected = meta.icon == i, onClick = { onChange(meta.copy(icon = i)) }, label = { Icon(i.vector(), contentDescription = i.name.lowercase()) })
            }
        }
        OutlinedTextField(meta.notes, { onChange(meta.copy(notes = it)) }, minLines = 3, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_notes)) })
    }
}
