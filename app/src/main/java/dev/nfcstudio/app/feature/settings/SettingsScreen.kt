package dev.nfcstudio.app.feature.settings

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
 * Settings screen. M1 explanatory placeholder; preferences with DataStore land
 * with M4. The theme currently follows the system setting.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ScreenPlaceholder(
            headline = stringResource(R.string.settings_placeholder_title),
            detail = stringResource(R.string.settings_placeholder_detail),
        )
    }
}
