package dev.nfcstudio.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Task
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.nfcstudio.app.R
import dev.nfcstudio.app.appContainer
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.feature.history.entryTitle
import dev.nfcstudio.app.ui.components.MessageCard
import dev.nfcstudio.app.ui.components.NfcStatusCard
import java.text.DateFormat
import java.util.Date

/** Home: real NFC status, entry points, recent scans and first-use guidance (spec 4.1). */
@Composable
fun HomeScreen(
    onOpenScan: () -> Unit,
    onOpenWrite: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenTasks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = appContainer()
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { initializer { container.homeViewModel() } })
    val recent by vm.recent.collectAsStateWithLifecycle()
    val templateCount by vm.templateCount.collectAsStateWithLifecycle()
    val nfcState by rememberCurrentNfcState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        NfcStatusCard(nfcState = nfcState)

        QuickAction(Icons.Filled.Nfc, stringResource(R.string.home_quick_scan), stringResource(R.string.home_quick_scan_detail), onOpenScan)
        QuickAction(
            Icons.AutoMirrored.Filled.Send,
            stringResource(R.string.home_quick_write),
            pluralStringResource(R.plurals.home_quick_write_detail, templateCount, templateCount),
            onOpenWrite,
        )
        QuickAction(Icons.Filled.Task, stringResource(R.string.nav_tasks), stringResource(R.string.home_quick_tasks_detail), onOpenTasks)

        Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        val list = recent
        if (list != null && list.isEmpty()) {
            MessageCard(title = stringResource(R.string.home_first_use_title), body = stringResource(R.string.home_first_use_detail))
        } else {
            list.orEmpty().forEach { entry ->
                QuickAction(
                    Icons.Filled.History,
                    entryTitle(entry),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.createdAt)),
                    onOpenHistory,
                )
            }
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, detail: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.heightIn(min = 72.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .weight(1f),
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
