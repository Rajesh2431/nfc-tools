package dev.nfcstudio.app.feature.advanced

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.apdu.CommandApdu
import dev.nfcstudio.app.core.apdu.StatusWords
import dev.nfcstudio.app.core.common.groupHexPairs
import dev.nfcstudio.app.core.common.toHex
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.Tone
import dev.nfcstudio.app.ui.components.displayText

@Composable
fun ApduConsoleScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = appContainer()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val s = settings ?: return
    if (!s.advancedMode) {
        MessageCard(title = stringResource(R.string.apdu_title), body = stringResource(R.string.apdu_needs_advanced), modifier = modifier.padding(16.dp)) {
            Button(onClick = onDone) { Text(stringResource(R.string.writer_done)) }
        }
        return
    }
    val vm: ApduConsoleViewModel = viewModel(
        key = "apdu-${s.allowStateChangingApdu}",
        factory = viewModelFactory { initializer { container.apduConsoleViewModel(s.allowStateChangingApdu) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.apdu_title), style = MaterialTheme.typography.headlineMedium) }
        item {
            MessageCard(
                title = stringResource(R.string.apdu_warning_title),
                body = stringResource(if (vm.stateChangingAllowed) R.string.apdu_warning_unlocked else R.string.apdu_warning),
                tone = if (vm.stateChangingAllowed) Tone.ERROR else Tone.NEUTRAL,
            )
        }
        if (!vm.available) {
            item {
                MessageCard(title = stringResource(R.string.apdu_no_tag), body = stringResource(R.string.apdu_no_tag_detail), tone = Tone.ERROR) {
                    Button(onClick = onDone) { Text(stringResource(R.string.writer_done)) }
                }
            }
            return@LazyColumn
        }
        item {
            OutlinedTextField(
                value = state.input,
                onValueChange = vm::setInput,
                label = { Text(stringResource(R.string.apdu_command)) },
                placeholder = { Text("00 A4 04 00 07 D2760000850101 00") },
                isError = state.problem != null || state.blocked,
                supportingText = {
                    when {
                        state.blocked -> Text(stringResource(R.string.apdu_blocked))
                        state.problem != null -> Text(stringResource(problemText(state.problem!!)))
                        else -> Text(stringResource(R.string.apdu_hint))
                    }
                },
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(stringResource(R.string.apdu_timeout), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(500, 1_000, 2_000, 5_000).forEach { ms ->
                    FilterChip(selected = state.timeoutMs == ms, onClick = { vm.setTimeout(ms) }, label = { Text("$ms ms") })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::send, enabled = !state.busy && state.input.isNotBlank()) { Text(stringResource(R.string.apdu_send)) }
                OutlinedButton(onClick = onDone) { Text(stringResource(R.string.apdu_close)) }
                if (state.transcript.isNotEmpty()) TextButton(onClick = vm::clearTranscript) { Text(stringResource(R.string.tasks_log_clear)) }
            }
        }
        items(state.transcript) { entry ->
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("> " + entry.commandHex.groupHexPairs(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        when (entry) {
                            is ApduConsoleViewModel.Entry.Response -> {
                                if (entry.response.data.isNotEmpty()) {
                                    Text("< " + entry.response.data.toHex().groupHexPairs(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "SW %04X · %s · %d ms".format(entry.response.sw, StatusWords.describe(entry.response.sw1, entry.response.sw2), entry.millis),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (entry.response.sw == 0x9000) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }

                            is ApduConsoleViewModel.Entry.Failure -> Text(entry.error.displayText(), color = MaterialTheme.colorScheme.error)
                            is ApduConsoleViewModel.Entry.NoStatusWord -> Text(stringResource(R.string.apdu_no_sw, entry.rawHex), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

private fun problemText(p: CommandApdu.Problem): Int = when (p) {
    CommandApdu.Problem.NOT_HEX -> R.string.apdu_problem_hex
    CommandApdu.Problem.TOO_SHORT -> R.string.apdu_problem_short
    CommandApdu.Problem.TOO_LONG -> R.string.apdu_problem_long
    CommandApdu.Problem.BAD_LENGTH -> R.string.apdu_problem_length
    CommandApdu.Problem.EXTENDED_UNSUPPORTED -> R.string.apdu_problem_extended
    CommandApdu.Problem.INVALID_CLA -> R.string.apdu_problem_cla
}
