package com.tapandgo.feature.advanced

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tapandgo.R
import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.common.toHex
import com.tapandgo.core.ndef.NdefLab
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.RecordValidator
import com.tapandgo.core.ndef.toRawSpec
import com.tapandgo.core.nfc.CurrentTagStore
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.di.PendingWriteStore
import com.tapandgo.ui.components.CopyButton
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.RecordRow
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class NdefLabViewModel @Inject constructor(
    private val tags: CurrentTagStore,
    private val pending: PendingWriteStore,
) : ViewModel() {
    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()
    private val _decoded = MutableStateFlow<NdefLab.Decoded?>(null)
    val decoded: StateFlow<NdefLab.Decoded?> = _decoded.asStateFlow()
    private val _records = MutableStateFlow(listOf(RecordSpec.Raw(NdefParser.TNF_WELL_KNOWN, "54", "", "02656e")))
    val records: StateFlow<List<RecordSpec.Raw>> = _records.asStateFlow()

    fun setInput(s: String) { _input.value = s.take(NdefLab.MAX_INPUT_BYTES * 3); _decoded.value = null }
    fun decode() { _decoded.value = NdefLab.decodeHex(_input.value) }

    /** Loads the NDEF message of the last scanned tag (memory only). */
    fun loadLastTag(): Boolean {
        val content = tags.current.value?.snapshot?.ndef?.content as? NdefContent.Records ?: return false
        setInput(content.rawMessageHex); decode(); return true
    }

    fun importDecoded() {
        (decoded.value as? NdefLab.Decoded.Ok)?.let { ok -> _records.value = ok.records.take(32).map { it.toRawSpec() } }
    }

    fun add() = _records.update { (it + RecordSpec.Raw(NdefParser.TNF_MIME_MEDIA, NdefLab.asciiToHex("text/plain")!!, "", "")).take(32) }
    fun remove(i: Int) = _records.update { it.filterIndexed { n, _ -> n != i } }
    fun duplicate(i: Int) = _records.update { (it.take(i + 1) + it[i] + it.drop(i + 1)).take(32) }
    fun move(i: Int, d: Int) = _records.update { l ->
        val j = i + d
        if (j !in l.indices) l else l.toMutableList().also { m -> val t = m[i]; m[i] = m[j]; m[j] = t }
    }
    fun change(i: Int, r: RecordSpec.Raw) = _records.update { l -> l.mapIndexed { n, old -> if (n == i) r else old } }

    /** Hands the built records to the Write tab (which shows size, capacity and confirmation). */
    fun sendToWriter(): Boolean {
        val list = _records.value
        if (list.isEmpty() || list.any { RecordValidator.validate(it) != null }) return false
        pending.records.value = list
        return true
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NdefLabScreen(onWrite: () -> Unit, modifier: Modifier = Modifier) {
    val vm: NdefLabViewModel = hiltViewModel()
    val input by vm.input.collectAsStateWithLifecycle()
    val decoded by vm.decoded.collectAsStateWithLifecycle()
    val records by vm.records.collectAsStateWithLifecycle()
    var noTag by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.lab_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard(stringResource(R.string.lab_decoder)) {
            OutlinedTextField(input, vm::setInput, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.lab_hex_input)) }, minLines = 3, textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::decode, enabled = input.isNotBlank()) { Text(stringResource(R.string.lab_decode)) }
                OutlinedButton(onClick = { noTag = !vm.loadLastTag() }) { Text(stringResource(R.string.lab_load_tag)) }
            }
            if (noTag) Text(stringResource(R.string.lab_no_tag), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            when (val d = decoded) {
                is NdefLab.Decoded.Error -> MessageCard(stringResource(R.string.lab_decode_error), body = d.reason, tone = Tone.ERROR)
                is NdefLab.Decoded.Ok -> {
                    Text(
                        stringResource(R.string.lab_decoded, d.records.size) + if (d.unwrappedFromTlv) " · " + stringResource(R.string.lab_tlv) else "",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    val decodedRecords = NdefParser.decodeAll(d.records)
                    decodedRecords.forEachIndexed { i, r -> RecordRow(i, r) }
                    d.records.forEachIndexed { i, r ->
                        Text("#${i + 1} TNF ${r.tnf} · type ${r.type.toHex().ifEmpty { "—" }} · id ${r.id.toHex().ifEmpty { "—" }} · ${r.payload.size} B", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = vm::importDecoded) { Text(stringResource(R.string.lab_edit_records)) }
                }
                null -> Unit
            }
        }

        SectionCard(stringResource(R.string.lab_builder)) {
            records.forEachIndexed { i, r -> RawRecordEditor(i, r, records.size, vm) }
            TextButton(onClick = vm::add, enabled = records.size < 32) { Text(stringResource(R.string.writer_add_record)) }
            val (bytes, badIndex) = NdefLab.encode(records)
            if (bytes != null) {
                Text(stringResource(R.string.lab_message_size, bytes.size), style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        NdefLab.hexdump(bytes), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    )
                    CopyButton(bytes.toHex(), stringResource(R.string.lab_message_hex))
                }
                Button(onClick = { if (vm.sendToWriter()) onWrite() }) { Text(stringResource(R.string.lab_write)) }
            } else if (badIndex != null && badIndex >= 0) {
                Text(stringResource(R.string.lab_record_invalid, badIndex + 1), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RawRecordEditor(i: Int, r: RecordSpec.Raw, size: Int, vm: NdefLabViewModel) {
    val problem = RecordValidator.validate(r)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lab_record_n, i + 1), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.move(i, -1) }, enabled = i > 0) { Icon(Icons.Filled.ArrowUpward, stringResource(R.string.writer_move_up)) }
                IconButton(onClick = { vm.move(i, 1) }, enabled = i < size - 1) { Icon(Icons.Filled.ArrowDownward, stringResource(R.string.writer_move_down)) }
                IconButton(onClick = { vm.duplicate(i) }, enabled = size < 32) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.wf_duplicate_block)) }
                IconButton(onClick = { vm.remove(i) }) { Icon(Icons.Filled.Close, stringResource(R.string.writer_remove)) }
            }
            Text("TNF", style = MaterialTheme.typography.labelMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TNF_NAMES.forEachIndexed { tnf, name -> FilterChip(selected = r.tnf == tnf, onClick = { vm.change(i, r.copy(tnf = tnf)) }, label = { Text("$tnf $name") }) }
            }
            val typeAscii = r.typeHex.hexToBytesOrNull()?.let { NdefParser.printableType(it) }
            OutlinedTextField(
                typeAscii ?: "", { s -> NdefLab.asciiToHex(s.take(255))?.let { vm.change(i, r.copy(typeHex = it)) } },
                modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(stringResource(R.string.lab_type_ascii)) },
            )
            HexField(r.typeHex, stringResource(R.string.lab_type_hex)) { vm.change(i, r.copy(typeHex = it)) }
            HexField(r.idHex, stringResource(R.string.lab_id_hex)) { vm.change(i, r.copy(idHex = it)) }
            HexField(r.payloadHex, stringResource(R.string.lab_payload_hex), lines = 3) { vm.change(i, r.copy(payloadHex = it)) }
            var text by androidx.compose.runtime.saveable.rememberSaveable(i) { androidx.compose.runtime.mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), singleLine = true, label = { Text(stringResource(R.string.lab_payload_text)) })
                TextButton(onClick = { vm.change(i, r.copy(payloadHex = NdefLab.utf8ToHex(text))) }) { Text(stringResource(R.string.lab_set)) }
            }
            r.payloadHex.hexToBytesOrNull()?.takeIf { it.isNotEmpty() }?.let {
                Text(NdefLab.hexdump(it, maxBytes = 512), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
            if (problem != null) Text(stringResource(R.string.lab_record_rules), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HexField(value: String, label: String, lines: Int = 1, onChange: (String) -> Unit) {
    val valid = value.hexToBytesOrNull() != null
    OutlinedTextField(
        value, { s -> onChange(s.filter { it.isLetterOrDigit() || it == ' ' }.take(NdefLab.MAX_INPUT_BYTES)) },
        modifier = Modifier.fillMaxWidth(), label = { Text(label) }, isError = !valid, minLines = lines, singleLine = lines == 1,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        supportingText = { Text(value.hexToBytesOrNull()?.let { "${it.size} B" } ?: stringResource(R.string.lab_hex_invalid)) },
    )
}

private val TNF_NAMES = listOf("Empty", "Well-known", "MIME", "Absolute URI", "External", "Unknown")
