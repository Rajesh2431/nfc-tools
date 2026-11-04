package com.tapandgo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tapandgo.R
import com.tapandgo.core.nfc.NfcState

/**
 * Status card summarizing the real NFC adapter state. Used on Home and Scan so
 * both destinations present one consistent status component.
 */
@Composable
fun NfcStatusCard(
    nfcState: NfcState,
    modifier: Modifier = Modifier,
    contentPadding: Boolean = true,
) {
    val icon: ImageVector
    val titleRes: Int
    val detailRes: Int
    when (nfcState) {
        NfcState.READY -> {
            icon = Icons.Filled.Nfc
            titleRes = R.string.nfc_state_ready
            detailRes = R.string.nfc_state_ready_detail
        }

        NfcState.DISABLED -> {
            icon = Icons.Filled.Nfc
            titleRes = R.string.nfc_state_disabled
            detailRes = R.string.nfc_state_disabled_detail
        }

        NfcState.NOT_AVAILABLE -> {
            icon = Icons.Filled.Settings
            titleRes = R.string.nfc_state_no_hardware
            detailRes = R.string.nfc_state_no_hardware_detail
        }
    }
    val title = stringResource(titleRes)
    val detail = stringResource(detailRes)

    Card(modifier = modifier.semantics { contentDescription = "$title. $detail" }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (nfcState == NfcState.DISABLED) {
                NfcSettingsLauncher()
            }
        }
    }
}

/**
 * Button opening the system NFC settings screen via the standard intent.
 * It is visible only while NFC is disabled, per the disabled-state requirement.
 */
@Composable
private fun NfcSettingsLauncher() {
    var openFailed by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    TextButton(onClick = {
        val intent = android.content.Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
        openFailed = runCatching { context.startActivity(intent) }.isFailure
    }) {
        Text(stringResource(R.string.nfc_state_disabled_action))
    }

    if (openFailed) {
        AlertDialog(
            onDismissRequest = { openFailed = false },
            confirmButton = {
                TextButton(onClick = { openFailed = false }) { Text(stringResource(android.R.string.ok)) }
            },
            text = { Text(stringResource(R.string.nfc_state_disabled_detail)) },
        )
    }
}
