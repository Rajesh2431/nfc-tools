package dev.nfcstudio.app.feature.writer

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
 * Write screen. M1 explains that the record builder and verified writing are
 * M3 work; nothing is ever written to a tag from this screen.
 */
@Composable
fun WriteScreen(modifier: Modifier = Modifier) {
    val nfcState = rememberCurrentNfcState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_write),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        NfcStatusCard(nfcState = nfcState.value)
        ScreenPlaceholder(
            headline = stringResource(R.string.write_placeholder_title),
            detail = stringResource(R.string.write_placeholder_detail),
        )
    }
}
