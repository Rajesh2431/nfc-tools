package dev.nfcstudio.app.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nfcstudio.app.R
import dev.nfcstudio.app.core.nfc.rememberCurrentNfcState
import dev.nfcstudio.app.ui.components.NfcStatusCard
import dev.nfcstudio.app.ui.components.ScreenPlaceholder

/**
 * Scan screen. M1 shows only the real NFC state and explains that discovery
 * arrives with M2; no mock tag reader and no fabricated scan results are used.
 */
@Composable
fun ScanScreen(modifier: Modifier = Modifier) {
    val nfcState = rememberCurrentNfcState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_scan),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        NfcStatusCard(nfcState = nfcState.value)
        ScreenPlaceholder(
            headline = stringResource(R.string.scan_placeholder_title),
            detail = stringResource(R.string.scan_placeholder_detail),
        )
    }
}
