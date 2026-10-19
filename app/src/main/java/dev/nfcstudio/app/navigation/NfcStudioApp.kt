package dev.nfcstudio.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.nfcstudio.app.feature.history.HistoryScreen
import dev.nfcstudio.app.feature.home.HomeScreen
import dev.nfcstudio.app.feature.scanner.ScanScreen
import dev.nfcstudio.app.feature.settings.SettingsScreen
import dev.nfcstudio.app.feature.tasks.TasksScreen
import dev.nfcstudio.app.feature.writer.WriteScreen

/**
 * App scaffold: bottom navigation bar plus a NavHost holding the six
 * top-level destinations from the specification.
 */
@Composable
fun NfcStudioApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.route == destination.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeScreen(
                    onOpenScan = { navController.navigate(TopLevelDestination.SCAN.route) },
                    onOpenWrite = { navController.navigate(TopLevelDestination.WRITE.route) },
                    onOpenHistory = { navController.navigate(TopLevelDestination.HISTORY.route) },
                )
            }
            composable(TopLevelDestination.SCAN.route) { ScanScreen() }
            composable(TopLevelDestination.WRITE.route) { WriteScreen() }
            composable(TopLevelDestination.TASKS.route) { TasksScreen() }
            composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
            composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        }
    }
}
