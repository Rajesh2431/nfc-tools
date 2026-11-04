package com.tapandgo.feature.scanner

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.tapandgo.R
import com.tapandgo.core.nfc.NdefContent
import com.tapandgo.core.nfc.NfcReaderEffect
import com.tapandgo.core.nfc.NfcState
import com.tapandgo.core.nfc.PlatformTag
import com.tapandgo.core.nfc.rememberCurrentNfcState
import com.tapandgo.domain.task.TaskValidator
import com.tapandgo.feature.tasks.MatchingProfiles
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.MessageCard
import com.tapandgo.ui.components.NfcStatusCard
import com.tapandgo.ui.components.TagDetailsCard
import com.tapandgo.ui.components.Tone
import com.tapandgo.ui.components.displayText
import kotlinx.coroutines.launch

/**
 * Read tab: reader mode is armed while shown; every tag becomes a truthful
 * snapshot (with chip identification) or a typed error. No mock data.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(
    onRunProfile: (id: Long, autoStarted: Boolean) -> Unit,
    onOpenApdu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ScanViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()
    val extras: ReadExtrasViewModel = hiltViewModel()
    val profiles by extras.profiles.collectAsStateWithLifecycle()
    val settings by extras.settings.collectAsStateWithLifecycle()
    val diagnosticsResult by extras.diagnosticsSaved.collectAsStateWithLifecycle()
    val diagnosticsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val snap = state.snapshot
        if (uri != null && snap != null) extras.exportDiagnostics(uri, snap, nfcState)
    }

    NfcReaderEffect { tag -> viewModel.onTag(PlatformTag(tag)) }

    val snapshot = state.snapshot
    val records = (snapshot?.ndef?.content as? NdefContent.Records)?.records.orEmpty()
    val matching = profiles.mapNotNull { p -> p.profile?.takeIf { TaskValidator.matches(it, records) } }

    // Opt-in auto-run (ADR 0001): start only when exactly one auto-run profile matches.
    var autoStartedFor by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(state.scanCount, matching) {
        val auto = matching.filter { it.autoRun }
        if (snapshot != null && auto.size == 1 && autoStartedFor != state.scanCount) {
            autoStartedFor = state.scanCount
            onRunProfile(auto.single().id, true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (nfcState != NfcState.READY) NfcStatusCard(nfcState = nfcState)
        val error = state.error
        when {
            state.working -> Waiting(working = true)
            error != null -> MessageCard(title = stringResource(R.string.scan_failed), body = error.displayText(), tone = Tone.ERROR) {
                Button(onClick = viewModel::clear) { Text(stringResource(R.string.scan_again)) }
            }

            snapshot != null -> key(state.scanCount) {
                MatchingProfiles(names = matching.map { it.id to it.name }, onRun = { onRunProfile(it, false) })
                TagDetailsCard(snapshot = snapshot)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::clear) { Text(stringResource(R.string.scan_clear)) }
                    OutlinedButton(onClick = { diagnosticsLauncher.launch("tapandgo-diagnostics.json") }) { Text(stringResource(R.string.diagnostics_export)) }
                    if (settings?.advancedMode == true && snapshot.supportsIsoDep) {
                        Button(onClick = onOpenApdu) { Text(stringResource(R.string.apdu_open)) }
                    }
                }
                diagnosticsResult?.let {
                    Text(stringResource(if (it) R.string.diagnostics_saved else R.string.backup_io_failed_detail), style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.scan_done_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            nfcState == NfcState.READY -> Waiting(working = false)
        }
    }
}

@Composable
private fun Waiting(working: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconBadge(Icons.Filled.Nfc, size = 120.dp)
        Text(
            stringResource(if (working) R.string.scan_working else R.string.scan_waiting_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        if (working) {
            CircularProgressIndicator(Modifier.size(32.dp))
        } else {
            Text(
                stringResource(R.string.scan_waiting_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
