package dev.nfcstudio.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.vector.ImageVector
import dev.nfcstudio.app.R

/**
 * The six fixed top-level destinations exposed by the bottom navigation bar.
 * String routes are kept small on purpose (see specification, section 5).
 */
enum class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    SCAN("scan", R.string.nav_scan, Icons.Filled.Nfc),
    WRITE("write", R.string.nav_write, Icons.AutoMirrored.Filled.Send),
    TASKS("tasks", R.string.nav_tasks, Icons.Filled.Task),
    HISTORY("history", R.string.nav_history, Icons.Filled.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings),
}
