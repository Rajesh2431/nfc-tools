package dev.nfcstudio.app.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.ndef.UriPolicy
import dev.nfcstudio.app.domain.task.TaskAction
import dev.nfcstudio.app.domain.task.TaskRunner
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.Tone

/** Step-by-step run: every external action waits for an explicit Run tap. */
@Composable
fun TaskRunScreen(profileId: Long, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val container = appContainer()
    val context = LocalContext.current
    val vm: TaskRunViewModel = viewModel(
        key = "run-$profileId",
        factory = viewModelFactory { initializer { container.taskRunViewModel(profileId, context) } },
    )
    val ui by vm.state.collectAsStateWithLifecycle()

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (val u = ui) {
            TaskRunViewModel.UiState.Loading -> MessageCard(title = stringResource(R.string.writer_writing), tone = Tone.WORKING)
            TaskRunViewModel.UiState.Unavailable -> MessageCard(
                title = stringResource(R.string.run_unavailable),
                body = stringResource(R.string.run_unavailable_detail),
                tone = Tone.ERROR,
            ) { Button(onClick = onDone) { Text(stringResource(R.string.writer_done)) } }

            is TaskRunViewModel.UiState.Running -> {
                Text(u.profile.name, style = MaterialTheme.typography.headlineMedium)
                val total = u.profile.actions.size
                when (val s = u.state) {
                    is TaskRunner.State.Confirm -> MessageCard(
                        title = stringResource(R.string.run_step, s.index + 1, total),
                        body = describe(s.action),
                    ) {
                        ExternalDetails(s.action)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::confirm) { Text(stringResource(R.string.run_confirm)) }
                            OutlinedButton(onClick = vm::skip) { Text(stringResource(R.string.run_skip)) }
                            TextButton(onClick = vm::cancel) { Text(stringResource(R.string.run_cancel)) }
                        }
                    }

                    is TaskRunner.State.Showing -> MessageCard(title = stringResource(R.string.run_step, s.index + 1, total)) {
                        InAppAction(s.action, container, context)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::continueAfterShowing) { Text(stringResource(R.string.run_continue)) }
                            TextButton(onClick = vm::cancel) { Text(stringResource(R.string.run_cancel)) }
                        }
                    }

                    is TaskRunner.State.Failed -> MessageCard(
                        title = stringResource(R.string.run_failed, s.index + 1),
                        body = stringResource(reasonLabel(s.reason)),
                        tone = Tone.ERROR,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = vm::retry) { Text(stringResource(R.string.run_retry)) }
                            OutlinedButton(onClick = vm::skip) { Text(stringResource(R.string.run_skip)) }
                            TextButton(onClick = vm::cancel) { Text(stringResource(R.string.run_cancel)) }
                        }
                    }

                    is TaskRunner.State.Finished -> MessageCard(
                        title = stringResource(R.string.run_finished),
                        tone = Tone.SUCCESS,
                    ) {
                        s.log.forEach { e ->
                            Text("#${e.actionIndex + 1} " + describe(e.action) + " — " + stringResource(outcomeLabel(e.outcome.name)), style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onDone) { Text(stringResource(R.string.writer_done)) }
                    }
                }
            }
        }
    }
}

/** For URLs, show the real host before the user confirms (spec § 10). */
@Composable
private fun ExternalDetails(action: TaskAction) {
    if (action !is TaskAction.OpenUrl) return
    val info = UriPolicy.inspect(action.url) ?: return
    info.host?.let { Text(stringResource(R.string.open_uri_host) + ": " + it, style = MaterialTheme.typography.titleSmall) }
    if (info.hasUserInfo) Text(stringResource(R.string.open_uri_warn_userinfo), color = MaterialTheme.colorScheme.error)
    if (info.hasNonAsciiHost) Text(stringResource(R.string.open_uri_warn_idn), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun InAppAction(action: TaskAction, container: dev.nfcstudio.app.AppContainer, context: android.content.Context) {
    var saveFailed by remember { mutableStateOf(false) }
    when (action) {
        is TaskAction.DisplayText -> SelectionContainer { Text(action.text, style = MaterialTheme.typography.bodyLarge) }
        is TaskAction.ShowContact -> {
            SelectionContainer {
                Column {
                    Text(action.name, style = MaterialTheme.typography.titleMedium)
                    if (action.phone.isNotEmpty()) Text(action.phone)
                    if (action.email.isNotEmpty()) Text(action.email)
                }
            }
            OutlinedButton(onClick = { saveFailed = container.actionPerformer(context).saveContact(action) != null }) {
                Text(stringResource(R.string.run_save_contact))
            }
            if (saveFailed) Text(stringResource(R.string.reason_no_app), color = MaterialTheme.colorScheme.error)
        }

        else -> Unit
    }
}
