package dev.nfcstudio.app.feature.history

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
import dev.nfcstudio.app.ui.components.ScreenPlaceholder

/**
 * History screen. M1 explanatory placeholder only; persistence, search,
 * favourites and export land with M4.
 */
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onBackToHome: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_history),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ScreenPlaceholder(
            headline = stringResource(R.string.history_placeholder_title),
            detail = stringResource(R.string.history_placeholder_detail),
            onBackToHome = onBackToHome,
        )
    }
}
