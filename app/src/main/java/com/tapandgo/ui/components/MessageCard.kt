package com.tapandgo.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class Tone { NEUTRAL, WORKING, SUCCESS, ERROR }

/**
 * The single result/status banner used across screens (spec § 9). It is a
 * polite live region so TalkBack announces state changes such as
 * "Write failed".
 */
@Composable
fun MessageCard(
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    tone: Tone = Tone.NEUTRAL,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = when (tone) {
                    Tone.ERROR -> MaterialTheme.colorScheme.error
                    Tone.SUCCESS -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            body?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (tone == Tone.WORKING) CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
            actions()
        }
    }
}
