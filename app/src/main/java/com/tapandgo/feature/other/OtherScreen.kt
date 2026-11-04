package com.tapandgo.feature.other

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tapandgo.R
import com.tapandgo.core.common.groupHexPairs
import com.tapandgo.core.common.hexToBytesOrNull
import com.tapandgo.core.common.toAsciiPreview
import com.tapandgo.core.nfc.ChipParsing
import com.tapandgo.core.nfc.MemoryDump
import com.tapandgo.core.nfc.NfcReaderEffect
import com.tapandgo.core.nfc.NfcState
import com.tapandgo.core.nfc.PlatformTag
import com.tapandgo.core.nfc.Verification
import com.tapandgo.core.nfc.rememberCurrentNfcState
import com.tapandgo.feature.other.OtherViewModel.Op
import com.tapandgo.feature.other.OtherViewModel.OtherError
import com.tapandgo.feature.other.OtherViewModel.Phase
import com.tapandgo.feature.writer.Confirm
import com.tapandgo.feature.writer.describeContent
import com.tapandgo.ui.components.CopyButton
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.NfcStatusCard
import com.tapandgo.ui.components.SectionCard
import com.tapandgo.ui.components.Tone
import com.tapandgo.ui.components.displayText

private fun Op.icon(): ImageVector = when (this) {
    Op.COPY -> Icons.Filled.ContentCopy
    Op.ERASE -> Icons.Filled.DeleteSweep
    Op.LOCK -> Icons.Filled.Lock
    Op.FORMAT -> Icons.Filled.Build
    Op.SET_PASSWORD -> Icons.Filled.Key
    Op.REMOVE_PASSWORD -> Icons.Filled.LockOpen
    Op.READ_MEMORY -> Icons.Filled.Memory
}

private fun Op.title(): Int = when (this) {
    Op.COPY -> R.string.op_copy
    Op.ERASE -> R.string.op_erase
    Op.LOCK -> R.string.op_lock
    Op.FORMAT -> R.string.op_format
    Op.SET_PASSWORD -> R.string.op_set_password
    Op.REMOVE_PASSWORD -> R.string.op_remove_password
    Op.READ_MEMORY -> R.string.op_read_memory
}

private fun Op.detail(): Int = when (this) {
    Op.COPY -> R.string.op_copy_detail
    Op.ERASE -> R.string.op_erase_detail
    Op.LOCK -> R.string.op_lock_detail
    Op.FORMAT -> R.string.op_format_detail
    Op.SET_PASSWORD -> R.string.op_set_password_detail
    Op.REMOVE_PASSWORD -> R.string.op_remove_password_detail
    Op.READ_MEMORY -> R.string.op_read_memory_detail
}

private val DESTRUCTIVE = setOf(Op.ERASE, Op.LOCK, Op.FORMAT, Op.SET_PASSWORD)

@Composable
fun OtherScreen(modifier: Modifier = Modifier) {
    val vm: OtherViewModel = hiltViewModel()
    val phase by vm.phase.collectAsStateWithLifecycle()
    val prepareError by vm.prepareError.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()
    val ready = nfcState == NfcState.READY

    NfcReaderEffect { tag -> vm.onTag(PlatformTag(tag)) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!ready) NfcStatusCard(nfcState = nfcState)
        when (val p = phase) {
            Phase.Menu -> SectionCard(stringResource(R.string.other_title)) {
                Op.entries.forEachIndexed { i, op ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = ready, role = Role.Button) { vm.start(op) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconBadge(
                            op.icon(),
                            container = if (op in DESTRUCTIVE) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
                            content = if (op in DESTRUCTIVE) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(stringResource(op.title()), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(op.detail()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }
            }

            is Phase.Prepare -> Prepare(p.op, prepareError, vm)
            is Phase.Awaiting -> MessageCard(
                title = stringResource(
                    when {
                        p.op == Op.COPY && !p.copyTarget -> R.string.copy_hold_source
                        p.op == Op.COPY -> R.string.copy_hold_target
                        else -> R.string.op_hold_tag
                    },
                    stringResource(p.op.title()),
                ),
                body = when {
                    p.op == Op.COPY && p.copyTarget -> stringResource(R.string.copy_target_detail, p.copiedBytes)
                    else -> stringResource(R.string.op_waiting_detail)
                },
                tone = Tone.WORKING,
            ) {
                prepareError?.let { Text(otherErrorText(it), color = MaterialTheme.colorScheme.error) }
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.writer_cancel)) }
            }

            is Phase.Working -> MessageCard(title = stringResource(R.string.writer_writing), body = stringResource(R.string.writer_writing_detail)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            is Phase.Confirm -> Unit
            is Phase.Done -> Done(p, vm)
            is Phase.Failed -> MessageCard(title = stringResource(R.string.writer_failed_title), body = otherErrorText(p.error), tone = Tone.ERROR) {
                Button(onClick = vm::dismissResult) { Text(stringResource(R.string.writer_done)) }
            }
        }
    }

    (phase as? Phase.Confirm)?.let { c -> ConfirmDialog(c, vm) }
}

@Composable
private fun Prepare(op: Op, error: OtherError?, vm: OtherViewModel) {
    when (op) {
        Op.LOCK -> MessageCard(title = stringResource(R.string.lock_intent_title), body = stringResource(R.string.lock_intent_detail), tone = Tone.ERROR) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::confirmLockIntent) { Text(stringResource(R.string.lock_intent_confirm)) }
                OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.writer_cancel)) }
            }
        }

        Op.SET_PASSWORD, Op.REMOVE_PASSWORD -> {
            var pwd by rememberSaveable { mutableStateOf("") }
            var repeat by rememberSaveable { mutableStateOf("") }
            var protectReads by rememberSaveable { mutableStateOf(false) }
            var understood by rememberSaveable { mutableStateOf(false) }
            val setting = op == Op.SET_PASSWORD
            SectionCard(stringResource(op.title())) {
                Text(
                    stringResource(if (setting) R.string.password_set_explain else R.string.password_remove_explain),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = pwd, onValueChange = { pwd = it }, singleLine = true,
                    label = { Text(stringResource(R.string.password_label, ChipParsing.MIN_PASSWORD)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (setting) {
                    OutlinedTextField(
                        value = repeat, onValueChange = { repeat = it }, singleLine = true,
                        label = { Text(stringResource(R.string.password_repeat)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        Modifier.fillMaxWidth().toggleable(protectReads, role = Role.Switch) { protectReads = it }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.password_protect_reads))
                            Text(stringResource(R.string.password_protect_reads_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = protectReads, onCheckedChange = null)
                    }
                    Row(
                        Modifier.fillMaxWidth().toggleable(understood, role = Role.Checkbox) { understood = it },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = understood, onCheckedChange = null)
                        Text(stringResource(R.string.password_understood), Modifier.padding(start = 8.dp), color = MaterialTheme.colorScheme.error)
                    }
                }
                error?.let { Text(otherErrorText(it), color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Button(onClick = { vm.submitPassword(pwd, if (setting) repeat else null, protectReads) }, enabled = !setting || understood) {
                        Text(stringResource(R.string.lock_intent_confirm))
                    }
                    OutlinedButton(onClick = vm::cancel) { Text(stringResource(R.string.writer_cancel)) }
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun ConfirmDialog(c: Phase.Confirm, vm: OtherViewModel) {
    val existing = c.existing?.let { describeContent(it) } ?: stringResource(R.string.content_unknown)
    val (title, body, confirm) = when (c.op) {
        Op.COPY -> Triple(R.string.overwrite_title, stringResource(R.string.overwrite_detail, existing), R.string.overwrite_confirm)
        Op.ERASE -> Triple(R.string.erase_title, stringResource(R.string.erase_detail, existing), R.string.erase_confirm)
        Op.LOCK -> Triple(R.string.lock_title, stringResource(R.string.lock_detail, existing), R.string.lock_confirm)
        Op.FORMAT -> Triple(R.string.format_title, stringResource(R.string.format_only_detail), R.string.op_format)
        Op.SET_PASSWORD -> Triple(R.string.password_confirm_title, stringResource(R.string.password_confirm_detail), R.string.op_set_password)
        else -> return
    }
    Confirm(stringResource(title), body, stringResource(confirm), onConfirm = vm::confirm, onDismiss = vm::decline, destructive = true)
}

@Composable
private fun Done(p: Phase.Done, vm: OtherViewModel) {
    val body = when (p.op) {
        Op.COPY, Op.ERASE -> stringResource(if (p.verification == Verification.VERIFIED) R.string.op_done_verified else R.string.writer_success_unverified)
        Op.LOCK -> stringResource(R.string.lock_success_detail)
        Op.FORMAT -> stringResource(R.string.format_done)
        Op.SET_PASSWORD -> stringResource(R.string.password_set_done)
        Op.REMOVE_PASSWORD -> stringResource(R.string.password_remove_done)
        Op.READ_MEMORY -> null
    }
    MessageCard(title = stringResource(R.string.op_done_title, stringResource(p.op.title())), body = body, tone = Tone.SUCCESS) {
        Button(onClick = vm::dismissResult) { Text(stringResource(R.string.writer_done)) }
    }
    p.dump?.let { MemoryView(it) }
}

@Composable
private fun MemoryView(dump: MemoryDump) {
    SectionCard(stringResource(R.string.memory_title)) {
        when (dump.note) {
            MemoryDump.Note.UNSUPPORTED -> Text(stringResource(R.string.memory_unsupported))
            MemoryDump.Note.STOPPED_AT_PROTECTED -> Text(stringResource(R.string.memory_stopped), color = MaterialTheme.colorScheme.error)
            MemoryDump.Note.PARTIAL_IO -> Text(stringResource(R.string.memory_partial), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
        if (dump.units.isNotEmpty()) {
            val all = dump.units.joinToString("\n") { it ?: "" }
            CopyButton(all, stringResource(R.string.memory_title))
            SelectionContainer {
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    dump.units.forEachIndexed { i, hex ->
                        val ascii = hex?.hexToBytesOrNull()?.toAsciiPreview().orEmpty()
                        Text(
                            "%03d  %s  %s".format(i, hex?.groupHexPairs() ?: "-- unreadable --", ascii),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun otherErrorText(e: OtherError): String = when (e) {
    is OtherError.Nfc -> e.error.displayText()
    OtherError.NothingToCopy -> stringResource(R.string.copy_nothing)
    OtherError.SameTag -> stringResource(R.string.copy_same_tag)
    OtherError.AlreadyFormatted -> stringResource(R.string.format_already)
    OtherError.PasswordMismatch -> stringResource(R.string.password_mismatch)
    OtherError.PasswordLength -> stringResource(R.string.password_length, ChipParsing.MIN_PASSWORD, ChipParsing.MAX_PASSWORD)
}
