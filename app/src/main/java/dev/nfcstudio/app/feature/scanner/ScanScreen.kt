package dev.nfcstudio.app.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.nfc.NdefContent
import dev.nfcstudio.app.core.nfc.NfcReaderEffect
import dev.nfcstudio.app.domain.task.TaskValidator
import dev.nfcstudio.app.feature.tasks.MatchingProfiles
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.PlatformTag
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.NfcStatusCard
import dev.nfcstudio.app.ui.components.TagDetailsCard
import dev.nfcstudio.app.ui.components.Tone
import dev.nfcstudio.app.ui.components.displayText

/**
 * Scan screen: reader mode is armed while this screen is shown; every
 * discovered tag becomes a truthful snapshot or a typed error. No mock data.
 */
@Composable
fun ScanScreen(
    onRunProfile: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = appContainer()
    val viewModel: ScanViewModel = viewModel(
        factory = viewModelFactory { initializer { container.scanViewModel() } },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()
    val profiles by container.tasks.observeProfiles().collectAsStateWithLifecycle(initialValue = emptyList())

    NfcReaderEffect { tag -> viewModel.onTag(PlatformTag(tag)) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_scan),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (nfcState != NfcState.READY) NfcStatusCard(nfcState = nfcState)

        val snapshot = state.snapshot
        val error = state.error
        when {
            state.working -> MessageCard(title = stringResource(R.string.scan_working), tone = Tone.WORKING)
            error != null -> MessageCard(
                title = stringResource(R.string.scan_failed),
                body = error.displayText(),
                tone = Tone.ERROR,
            ) {
                Button(onClick = viewModel::clear) { Text(stringResource(R.string.scan_again)) }
            }

            snapshot != null -> key(state.scanCount) {
                MessageCard(
                    title = stringResource(R.string.scan_done),
                    body = stringResource(R.string.scan_done_detail),
                    tone = Tone.SUCCESS,
                ) {
                    OutlinedButton(onClick = viewModel::clear) { Text(stringResource(R.string.scan_clear)) }
                }
                val records = (snapshot.ndef?.content as? NdefContent.Records)?.records.orEmpty()
                MatchingProfiles(
                    names = profiles.mapNotNull { p -> p.profile?.takeIf { TaskValidator.matches(it, records) }?.let { p.id to p.name } },
                    onRun = onRunProfile,
                )
                TagDetailsCard(snapshot = snapshot)
            }

            else -> MessageCard(
                title = stringResource(R.string.scan_waiting_title),
                body = stringResource(R.string.scan_waiting_detail),
            )
        }
    }
}
