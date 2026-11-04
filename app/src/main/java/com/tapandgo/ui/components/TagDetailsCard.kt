package com.tapandgo.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tapandgo.R
import com.tapandgo.core.common.groupHexPairs
import com.tapandgo.core.ndef.DecodedRecord
import com.tapandgo.core.ndef.RecordKind
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.core.nfc.TagSnapshot

/**
 * A real discovered tag as icon rows (spec 4.2). Values Android does not
 * expose show as unknown; nothing is guessed.
 */
@Composable
fun TagDetailsCard(snapshot: TagSnapshot, modifier: Modifier = Modifier) {
    val unknown = stringResource(R.string.value_unknown)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard(stringResource(R.string.tag_details)) {
            val chip = snapshot.chip
            InfoRow(
                Icons.Filled.Category,
                stringResource(R.string.tag_type),
                listOfNotNull(chip?.vendor, chip?.name).joinToString(" ").ifBlank { stringResource(R.string.tag_type_unknown) },
            )
            InfoRow(Icons.Filled.Layers, stringResource(R.string.tag_technologies), snapshot.technologies.joinToString(", "))
            InfoRow(
                Icons.Filled.Fingerprint,
                stringResource(R.string.tag_uid),
                snapshot.uidHex?.groupHexPairs()?.replace(' ', ':') ?: stringResource(R.string.value_not_exposed),
                monospace = snapshot.uidHex != null,
                trailing = snapshot.uidHex?.let { uid -> { CopyButton(uid.groupHexPairs().replace(' ', ':'), stringResource(R.string.tag_uid)) } },
            )
            val nfcA = snapshot.techDetails.firstOrNull { it.technology.startsWith("NfcA") }?.properties?.toMap()
            nfcA?.get("ATQA")?.let { InfoRow(Icons.Filled.Tag, "ATQA", "0x$it", monospace = true) }
            nfcA?.get("SAK")?.let { InfoRow(Icons.Filled.Tag, "SAK", "0x$it", monospace = true) }
            if (chip?.totalBytes != null) {
                InfoRow(
                    Icons.Filled.Memory,
                    stringResource(R.string.tag_memory),
                    if (chip.unitCount != null && chip.unitSize != null) {
                        stringResource(R.string.tag_memory_value, chip.totalBytes, chip.unitCount, chip.unitSize)
                    } else {
                        stringResource(R.string.value_bytes, chip.totalBytes)
                    },
                )
            }
            val ndef = snapshot.ndef
            if (ndef == null) {
                InfoRow(
                    Icons.Filled.Description,
                    stringResource(R.string.tag_data_format),
                    stringResource(if (snapshot.formatable) R.string.ndef_formatable_short else R.string.ndef_none_short),
                )
            } else {
                InfoRow(Icons.Filled.Description, stringResource(R.string.tag_data_format), ndefTypeName(ndef.ndefType) ?: unknown)
                val used = (ndef.content as? NdefContent.Records)?.sizeBytes ?: 0
                InfoRow(
                    Icons.Filled.DataUsage,
                    stringResource(R.string.tag_size),
                    ndef.maxCapacityBytes?.let { stringResource(R.string.tag_size_value, used, it) } ?: unknown,
                )
                InfoRow(Icons.Filled.Edit, stringResource(R.string.tag_writable), yesNoUnknown(ndef.isWritable))
                InfoRow(Icons.Filled.Lock, stringResource(R.string.tag_can_lock), yesNoUnknown(ndef.canMakeReadOnly))
            }
            Text(stringResource(R.string.tag_uid_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val ndef = snapshot.ndef
        if (ndef != null) {
            SectionCard(
                (ndef.content as? NdefContent.Records)?.let { pluralStringResource(R.plurals.tag_records, it.records.size, it.records.size) }
                    ?: stringResource(R.string.tag_records_title),
            ) {
                if (ndef.fromCache) Text(stringResource(R.string.tag_from_cache), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                when (val content = ndef.content) {
                    NdefContent.Empty -> Muted(stringResource(R.string.ndef_empty))
                    NdefContent.Malformed -> Text(stringResource(R.string.ndef_malformed), color = MaterialTheme.colorScheme.error)
                    NdefContent.Unreadable -> Text(stringResource(R.string.ndef_unreadable), color = MaterialTheme.colorScheme.error)
                    is NdefContent.Records -> {
                        content.records.forEachIndexed { i, r ->
                            if (i > 0) HorizontalDivider()
                            RecordRow(index = i + 1, record = r)
                        }
                        HorizontalDivider()
                        RawBlock(hex = content.rawMessageHex, label = stringResource(R.string.raw_message))
                    }
                }
            }
        }

        if (snapshot.techDetails.isNotEmpty()) TechDetailsSection(snapshot)
    }
}

@Composable
private fun TechDetailsSection(snapshot: TagSnapshot) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SectionCard(stringResource(R.string.tech_details_title)) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.tech_details_hide else R.string.tech_details_show))
        }
        AnimatedVisibility(expanded) {
            Column {
                snapshot.techDetails.forEach { detail ->
                    Text(detail.technology, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                    detail.properties.forEach { (label, value) -> DetailRow(label, value, monospace = true) }
                }
                Muted(stringResource(R.string.tech_details_note))
            }
        }
    }
}

@Composable
private fun yesNoUnknown(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.value_yes
        false -> R.string.value_no
        null -> R.string.value_unknown
    },
)

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DetailRow(label: String, value: String, copyable: Boolean = false, monospace: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = if (monospace) FontFamily.Monospace else null) }
        }
        if (copyable) CopyButton(value = value, label = label)
    }
}

/** Selectable, copyable hex + printable-ASCII view (spec § 9). */
@Composable
fun RawBlock(hex: String, label: String, ascii: String? = null, truncated: Boolean = false) {
    var show by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { show = !show }) {
            Text(stringResource(if (show) R.string.raw_hide_named else R.string.raw_show_named, label))
        }
        if (show) CopyButton(value = hex, label = label)
    }
    if (!show) return
    SelectionContainer {
        Text(hex.chunked(32).joinToString("\n") { it.groupHexPairs() }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
    ascii?.let {
        Text(stringResource(R.string.raw_ascii), style = MaterialTheme.typography.labelMedium)
        SelectionContainer { Text(it.chunked(16).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
    }
    if (truncated) Muted(stringResource(R.string.raw_truncated))
}

/** One decoded record: icon, kind, value, structured details, raw bytes, safe link opening. */
@Composable
fun RecordRow(index: Int, record: DecodedRecord) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    var openFailed by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(recordIcon(record), container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    "#$index · " + stringResource(recordKindLabel(record.kind)) + (record.mimeType?.takeIf { record.kind == RecordKind.MIME }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val primary = when (record.kind) {
                    RecordKind.TEXT -> record.text
                    RecordKind.URI, RecordKind.SMART_POSTER -> record.uri
                    RecordKind.MIME -> record.asciiPreview?.take(80)
                    else -> record.details.firstOrNull { !it.secret }?.value ?: record.typeName
                }
                Text(primary.orEmpty().ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge, maxLines = if (expanded) 20 else 2)
            }
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = stringResource(if (expanded) R.string.collapse else R.string.expand))
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 56.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                record.text?.takeIf { record.kind == RecordKind.SMART_POSTER }?.let { DetailRow(stringResource(R.string.detail_title), it) }
                record.language?.let { DetailRow(stringResource(R.string.writer_language_label), it) }
                record.uri?.let { DetailRow(stringResource(R.string.record_uri), it, copyable = true) }
                record.text?.takeIf { record.kind == RecordKind.TEXT }?.let { CopyButton(it, stringResource(R.string.record_text)) }
                record.details.forEach { d -> SecretAwareDetail(stringResource(detailLabel(d.label)), d.value, d.secret) }
                record.note?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = if (record.kind == RecordKind.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Muted(stringResource(R.string.record_meta, record.tnf, record.payloadSize))
                if (record.uri != null) FilledTonalButton(onClick = { confirmOpen = true }) { Text(stringResource(R.string.open_uri)) }
                record.rawHex?.let { RawBlock(hex = it, label = stringResource(R.string.raw_payload), ascii = record.asciiPreview, truncated = record.rawHexTruncated) }
            }
        }
    }

    if (confirmOpen && record.uri != null) {
        OpenLinkDialog(uri = record.uri, onDismiss = { confirmOpen = false }, onResult = { openFailed = !it })
    }
    if (openFailed) {
        AlertDialog(
            onDismissRequest = { openFailed = false },
            text = { Text(stringResource(R.string.open_uri_failed)) },
            confirmButton = { TextButton(onClick = { openFailed = false }) { Text(stringResource(android.R.string.ok)) } },
        )
    }
}

@Composable
private fun SecretAwareDetail(label: String, value: String, secret: Boolean) {
    var reveal by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer { Text(if (secret && !reveal) "••••••••" else value, style = MaterialTheme.typography.bodyLarge) }
        }
        if (secret) {
            IconButton(onClick = { reveal = !reveal }) {
                Icon(if (reveal) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = stringResource(if (reveal) R.string.hide_secret else R.string.show_secret))
            }
        }
        if (!secret || reveal) CopyButton(value, label)
    }
}
