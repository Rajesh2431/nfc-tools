package com.tapandgo.feature.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.tapandgo.R
import com.tapandgo.ui.components.IconBadge
import com.tapandgo.ui.components.SectionCard

/** One entry in the Tools hub. Only implemented features are listed (no placeholders). */
data class ToolEntry(val icon: ImageVector, val title: Int, val detail: Int, val onClick: () -> Unit)

@Composable
fun ToolsScreen(
    onTagOperations: () -> Unit,
    onHistory: () -> Unit,
    onLibrary: () -> Unit,
    onDeveloper: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ToolGroup(
            R.string.tools_group_tags,
            listOf(
                ToolEntry(Icons.Filled.CollectionsBookmark, R.string.library_title, R.string.tools_library_detail, onLibrary),
                ToolEntry(Icons.Filled.Tune, R.string.tools_tag_ops, R.string.tools_tag_ops_detail, onTagOperations),
                ToolEntry(Icons.Filled.History, R.string.nav_history, R.string.tools_history_detail, onHistory),
            ),
        )
        ToolGroup(
            R.string.tools_group_developer,
            listOf(ToolEntry(Icons.Filled.Terminal, R.string.tools_developer, R.string.tools_developer_detail, onDeveloper)),
        )
        ToolGroup(
            R.string.tools_group_app,
            listOf(
                ToolEntry(Icons.Filled.Settings, R.string.nav_settings, R.string.tools_settings_detail, onSettings),
                ToolEntry(Icons.Filled.Info, R.string.about_title, R.string.tools_about_detail, onAbout),
            ),
        )
    }
}

@Composable
private fun ToolGroup(title: Int, entries: List<ToolEntry>) {
    SectionCard(stringResource(title)) {
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = e.onClick)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(e.icon)
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(stringResource(e.title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(e.detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
    }
}
