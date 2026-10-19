package dev.nfcstudio.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.nfc.NfcState
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.ui.components.NfcStatusCard

/**
 * Home screen: truthful NFC status plus quick actions into the main flows.
 * No fake or sample tag payloads are shown at any point.
 */
@Composable
fun HomeScreen(
    onOpenScan: () -> Unit,
    onOpenWrite: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nfcState = rememberCurrentNfcState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )

        NfcStatusCard(nfcState = nfcState.value)

        QuickAction(
            icon = Icons.Filled.Nfc,
            label = stringResource(R.string.home_quick_scan),
            detail = stringResource(R.string.home_quick_scan_detail),
            onClick = onOpenScan,
        )
        QuickAction(
            icon = Icons.AutoMirrored.Filled.Send,
            label = stringResource(R.string.home_quick_write),
            detail = stringResource(R.string.home_quick_write_detail),
            onClick = onOpenWrite,
        )
        QuickAction(
            icon = Icons.Filled.History,
            label = stringResource(R.string.nav_history),
            detail = stringResource(R.string.history_placeholder_title),
            onClick = onOpenHistory,
        )

        Text(
            text = stringResource(R.string.home_upcoming),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.home_upcoming_detail),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    detail: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 72.dp),
    ) {
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
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
