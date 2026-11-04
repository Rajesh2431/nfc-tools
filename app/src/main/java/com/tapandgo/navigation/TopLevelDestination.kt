package com.tapandgo.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Task
import androidx.compose.ui.graphics.vector.ImageVector
import com.tapandgo.R

/** Bottom-bar destinations. Settings lives in the top app bar. */
enum class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    READ("read", R.string.nav_read, Icons.Filled.Nfc),
    WRITE("write", R.string.nav_write, Icons.AutoMirrored.Filled.Send),
    TASKS("tasks", R.string.nav_tasks, Icons.Filled.Task),
    TOOLS("tools", R.string.nav_tools, Icons.Filled.Build),
}
