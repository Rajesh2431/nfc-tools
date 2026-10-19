package dev.nfcstudio.app.feature.tasks

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
 * Task profiles screen. M1 explanatory placeholder only; concrete allowlisted
 * actions and confirmation policy land with M5.
 */
@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_tasks),
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        ScreenPlaceholder(
            headline = stringResource(R.string.tasks_placeholder_title),
            detail = stringResource(R.string.tasks_placeholder_detail),
        )
    }
}
