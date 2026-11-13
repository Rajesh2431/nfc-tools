package com.tapandgo.feature.emulation

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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.tapandgo.R
import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.ndef.NdefLab
import com.tapandgo.core.ndef.NdefMessageCodec
import com.tapandgo.core.ndef.NdefParser
import com.tapandgo.core.ndef.RecordSpec
import com.tapandgo.core.ndef.RecordValidator
import com.tapandgo.core.ndef.toRawRecord
import com.tapandgo.core.nfc.CurrentTagStore
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.nfc.hce.ApduLogEntry
import com.tapandgo.nfc.hce.EmulationStore
import com.tapandgo.nfc.hce.Type4Emulator
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.RecordRow
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class EmulationViewModel @Inject constructor(
    private val store: EmulationStore,
    private val tags: CurrentTagStore,
) : ViewModel() {
    val enabled: StateFlow<Boolean> = store.enabled
    val log: StateFlow<List<ApduLogEntry>> = store.log
    val supported get() = store.hceSupported
    private val _message = MutableStateFlow(store.message())
    val message: StateFlow<ByteArray?> = _message.asStateFlow()
    private val _problem = MutableStateFlow<Int?>(null)
    val problem: StateFlow<Int?> = _problem.asStateFlow()

    /** Builds a one-record message from the form (URI or text) and saves it as the profile. */
    fun useRecord(spec: RecordSpec) {
        if (RecordValidator.validate(spec) != null) { _problem.value = R.string.emu_invalid; return }
        save(NdefMessageCodec.encode(listOf(spec.toRawRecord())))
    }

    fun useLastTag() {
        val c = tags.current.value?.snapshot?.ndef?.content as? NdefContent.Records
        val bytes = c?.rawMessageHex?.hexToBytesOrNull()
        if (bytes == null) { _problem.value = R.string.lab_no_tag; return }
        save(bytes)
    }

    fun useHex(hex: String) {
        when (val d = NdefLab.decodeHex(hex)) {
            is NdefLab.Decoded.Ok -> save(NdefMessageCodec.encode(d.records))
            is NdefLab.Decoded.Error -> _problem.value = R.string.emu_invalid
        }
    }

    private fun save(bytes: ByteArray) {
        if (bytes.size > Type4Emulator.MAX_MESSAGE || !store.save(bytes)) { _problem.value = R.string.emu_too_big; return }
        _problem.value = null
        _message.value = bytes
    }

    fun setEnabled(on: Boolean) = viewModelScope.launch {
        if (!store.setEnabled(on)) _problem.value = R.string.emu_enable_failed
    }

    fun clearLog() = store.clearLog()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmulationScreen(modifier: Modifier = Modifier) {
    val vm: EmulationViewModel = hiltViewModel()
    val enabled by vm.enabled.collectAsStateWithLifecycle()
    val log by vm.log.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val problem by vm.problem.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(0) }
    // Reader mode disables card emulation; keep it off while this screen is open.
    com.tapandgo.core.nfc.PauseNfcReader()
    var value by rememberSaveable { mutableStateOf("") }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!vm.supported) {
            MessageCard(stringResource(R.string.emu_unsupported), body = stringResource(R.string.emu_unsupported_detail), tone = Tone.ERROR)
            return@Column
        }
        MessageCard(
            stringResource(if (enabled) R.string.emu_on else R.string.emu_off),
            body = stringResource(if (enabled) R.string.emu_on_detail else R.string.emu_off_detail) + "\n" + stringResource(R.string.emu_reader_note),
            tone = if (enabled) Tone.SUCCESS else Tone.NEUTRAL,
        ) {
            Row(Modifier.fillMaxWidth().toggleable(enabled, enabled = message != null, role = Role.Switch, onValueChange = vm::setEnabled), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.emu_switch), Modifier.weight(1f))
                Switch(enabled, null, enabled = message != null)
            }
        }
        problem?.let { MessageCard(stringResource(it), tone = Tone.ERROR) }

        SectionCard(stringResource(R.string.emu_profile)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.record_uri, R.string.record_text, R.string.lab_message_hex).forEachIndexed { i, res ->
                    FilterChip(selected = kind == i, onClick = { kind = i }, label = { Text(stringResource(res)) })
                }
            }
            OutlinedTextField(value, { value = it.take(8000) }, modifier = Modifier.fillMaxWidth(), minLines = if (kind == 2) 3 else 1, label = { Text(stringResource(R.string.emu_value)) })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    when (kind) {
                        0 -> vm.useRecord(RecordSpec.Uri(value.trim()))
                        1 -> vm.useRecord(RecordSpec.Text(value))
                        else -> vm.useHex(value)
                    }
                }, enabled = value.isNotBlank()) { Text(stringResource(R.string.emu_use)) }
                OutlinedButton(onClick = vm::useLastTag) { Text(stringResource(R.string.lab_load_tag)) }
            }
            Text(stringResource(R.string.emu_profile_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        message?.let { bytes ->
            SectionCard(stringResource(R.string.emu_preview, bytes.size)) {
                runCatching { NdefParser.decodeAll(NdefMessageCodec.parse(bytes)) }.getOrNull()?.forEachIndexed { i, r -> RecordRow(i, r) }
                Text(NdefLab.hexdump(bytes, maxBytes = 1024), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.horizontalScroll(rememberScrollState()))
            }
        }

        SectionCard(stringResource(R.string.emu_log)) {
            if (log.isEmpty()) Text(stringResource(R.string.emu_log_empty), style = MaterialTheme.typography.bodySmall)
            val fmt = DateFormat.getTimeInstance(DateFormat.MEDIUM)
            log.takeLast(60).reversed().forEach { e ->
                Text("${fmt.format(Date(e.time))}  ${e.command} → ${e.status.uppercase()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            }
            if (log.isNotEmpty()) TextButton(onClick = vm::clearLog) { Text(stringResource(R.string.tasks_log_clear)) }
        }

        Text(stringResource(R.string.emu_compat), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
