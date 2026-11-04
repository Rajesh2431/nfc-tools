package dev.nfcstudio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.common.groupHexPairs
import dev.nfcstudio.app.core.ndef.DecodedRecord
import dev.nfcstudio.app.core.ndef.RecordKind
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.TagSnapshot
import dev.nfcstudio.app.core.nfc.TechDetail

/**
 * A real discovered tag: UID, technologies, NDEF facts and decoded records.
 * Unknown values render as unknown; malformed records show their note; URIs
 * open only through [OpenLinkDialog].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDetailsCard(snapshot: TagSnapshot, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.tag_details), style = MaterialTheme.typography.titleMedium)

            DetailRow(
                label = stringResource(R.string.tag_uid),
                value = snapshot.uidHex?.groupHexPairs() ?: stringResource(R.string.value_not_exposed),
                copyable = snapshot.uidHex != null,
            )
            Text(
                stringResource(R.string.tag_uid_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(stringResource(R.string.tag_technologies), style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.technologies.forEach { TechChip(it) }
            }

            HorizontalDivider()
            NdefSection(snapshot)

            if (snapshot.techDetails.isNotEmpty()) {
                HorizontalDivider()
                TechDetailsSection(snapshot.techDetails)
            }
        }
    }
}

@Composable
private fun NdefSection(snapshot: TagSnapshot) {
    val ndef = snapshot.ndef
    if (ndef == null) {
        Muted(stringResource(R.string.ndef_none))
        if (snapshot.formatable) Muted(stringResource(R.string.ndef_formatable_note))
        return
    }
    ndef.ndefType?.let { DetailRow(stringResource(R.string.tag_ndef_type), it) }
    DetailRow(
        stringResource(R.string.tag_capacity),
        ndef.maxCapacityBytes?.let { stringResource(R.string.value_bytes, it) } ?: stringResource(R.string.value_unknown),
    )
    DetailRow(stringResource(R.string.tag_writable), yesNoUnknown(ndef.isWritable))
    DetailRow(stringResource(R.string.tag_can_lock), yesNoUnknown(ndef.canMakeReadOnly))
    if (ndef.fromCache) {
        Text(
            stringResource(R.string.tag_from_cache),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    when (val content = ndef.content) {
        NdefContent.Empty -> Muted(stringResource(R.string.ndef_empty))
        NdefContent.Malformed -> Text(stringResource(R.string.ndef_malformed), color = MaterialTheme.colorScheme.error)
        NdefContent.Unreadable -> Text(stringResource(R.string.ndef_unreadable), color = MaterialTheme.colorScheme.error)
        is NdefContent.Records -> {
            DetailRow(stringResource(R.string.tag_message_size), stringResource(R.string.value_bytes, content.sizeBytes))
            Text(
                pluralStringResource(R.plurals.tag_records, content.records.size),
                style = MaterialTheme.typography.titleMedium,
            )
            content.records.forEachIndexed { index, record -> RecordRow(index = index + 1, record = record) }
            RawBlock(hex = content.rawMessageHex, label = stringResource(R.string.raw_message))
        }
    }
}

@Composable
private fun TechDetailsSection(details: List<TechDetail>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) R.string.tech_details_hide else R.string.tech_details_show))
    }
    if (!expanded) return
    details.forEach { detail ->
        Text(detail.technology, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        detail.properties.forEach { (label, value) -> DetailRow(label, value, monospace = true) }
    }
    Muted(stringResource(R.string.tech_details_note))
}

@Composable
private fun pluralStringResource(id: Int, count: Int): String =
    androidx.compose.ui.res.pluralStringResource(id, count, count)

@Composable
private fun yesNoUnknown(value: Boolean?): String = stringResource(
    when (value) {
        true -> R.string.value_yes
        false -> R.string.value_no
        null -> R.string.value_unknown
    },
)

@Composable
fun TechChip(name: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(name, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DetailRow(label: String, value: String, copyable: Boolean = false, monospace: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = if (monospace) FontFamily.Monospace else null,
                )
            }
        }
        if (copyable) CopyButton(value = value, label = label)
    }
}

/** Selectable, copyable hex + printable-ASCII view (spec § 9). */
@Composable
private fun RawBlock(hex: String, label: String, ascii: String? = null, truncated: Boolean = false) {
    var show by rememberSaveable { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { show = !show }) {
            Text(stringResource(if (show) R.string.raw_hide_named else R.string.raw_show_named, label))
        }
        if (show) CopyButton(value = hex, label = label)
    }
    if (!show) return
    SelectionContainer {
        Text(
            hex.chunked(32).joinToString("\n") { it.groupHexPairs() },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
        )
    }
    ascii?.let {
        Text(stringResource(R.string.raw_ascii), style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(it.chunked(16).joinToString("\n"), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
    }
    if (truncated) Muted(stringResource(R.string.raw_truncated))
}

/** One decoded record: kind, decoded content, raw bytes, confirmed link opening. */
@Composable
fun RecordRow(index: Int, record: DecodedRecord) {
    var confirmOpen by rememberSaveable { mutableStateOf(false) }
    var openFailed by rememberSaveable { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "#$index " + stringResource(recordKindLabel(record.kind)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            when (record.kind) {
                RecordKind.TEXT -> {
                    ValueWithCopy(record.text.orEmpty(), stringResource(R.string.record_text))
                    record.language?.let { Muted(stringResource(R.string.record_language, it)) }
                }

                RecordKind.URI, RecordKind.SMART_POSTER -> {
                    record.text?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                    ValueWithCopy(record.uri.orEmpty(), stringResource(R.string.record_uri))
                    Button(onClick = { confirmOpen = true }) { Text(stringResource(R.string.open_uri)) }
                }

                RecordKind.MIME -> Text(
                    stringResource(R.string.record_mime_type, record.mimeType.orEmpty()),
                    style = MaterialTheme.typography.bodyMedium,
                )

                RecordKind.EMPTY, RecordKind.UNKNOWN -> record.typeName?.let {
                    Muted(stringResource(R.string.record_type, it))
                }
            }
            record.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (record.kind == RecordKind.UNKNOWN) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Muted(stringResource(R.string.record_meta, record.tnf, record.payloadSize))
            record.rawHex?.let {
                RawBlock(hex = it, label = stringResource(R.string.raw_payload), ascii = record.asciiPreview, truncated = record.rawHexTruncated)
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
private fun ValueWithCopy(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SelectionContainer(modifier = Modifier.weight(1f)) { Text(value, style = MaterialTheme.typography.bodyLarge) }
        CopyButton(value = value, label = label)
    }
}

fun recordKindLabel(kind: RecordKind): Int = when (kind) {
    RecordKind.TEXT -> R.string.record_text
    RecordKind.URI -> R.string.record_uri
    RecordKind.SMART_POSTER -> R.string.record_smart_poster
    RecordKind.MIME -> R.string.record_mime
    RecordKind.EMPTY -> R.string.record_empty
    RecordKind.UNKNOWN -> R.string.record_unknown
}
