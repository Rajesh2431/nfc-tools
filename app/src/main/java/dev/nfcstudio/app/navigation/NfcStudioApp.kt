package dev.nfcstudio.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.nfcstudio.app.feature.advanced.ApduConsoleScreen
import dev.nfcstudio.app.feature.history.HistoryScreen
import dev.nfcstudio.app.feature.home.HomeScreen
import dev.nfcstudio.app.feature.scanner.ScanScreen
import dev.nfcstudio.app.feature.settings.SettingsScreen
import dev.nfcstudio.app.feature.tasks.ProfileEditorScreen
import dev.nfcstudio.app.feature.tasks.TaskRunScreen
import dev.nfcstudio.app.feature.tasks.TasksScreen
import dev.nfcstudio.app.feature.writer.WriteScreen

private const val EDIT_ROUTE = "tasks/edit/{id}"
private const val RUN_ROUTE = "tasks/run/{id}"
private const val APDU_ROUTE = "scan/apdu"

/**
 * App scaffold: bottom navigation on phones, a navigation rail on wide
 * screens (>= 600 dp, spec P1 tablet layouts), plus the NavHost.
 */
@Composable
fun NfcStudioApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp

    fun isSelected(d: TopLevelDestination) = route == d.route || route?.startsWith(d.route + "/") == true
    fun go(d: TopLevelDestination) {
        if (d == TopLevelDestination.HOME) {
            // Home is the start destination: restoreState would bring back the
            // last saved tab stack (e.g. Scan) instead of Home, so just pop to it.
            navController.popBackStack(navController.graph.findStartDestination().id, inclusive = false)
            return
        }
        navController.navigate(d.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        bottomBar = {
            if (!wide) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { d ->
                        NavigationBarItem(
                            selected = isSelected(d),
                            onClick = { go(d) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(stringResource(d.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Row(Modifier.fillMaxSize().padding(innerPadding)) {
            if (wide) {
                NavigationRail {
                    TopLevelDestination.entries.forEach { d ->
                        NavigationRailItem(
                            selected = isSelected(d),
                            onClick = { go(d) },
                            icon = { Icon(d.icon, contentDescription = null) },
                            label = { Text(stringResource(d.labelRes)) },
                        )
                    }
                }
            }
            AppNavHost(navController, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController, modifier: Modifier) {
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(navController = navController, startDestination = TopLevelDestination.HOME.route, modifier = modifier) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(
                onOpenScan = { navController.navigate(TopLevelDestination.SCAN.route) },
                onOpenWrite = { navController.navigate(TopLevelDestination.WRITE.route) },
                onOpenHistory = { navController.navigate(TopLevelDestination.HISTORY.route) },
                onOpenTasks = { navController.navigate(TopLevelDestination.TASKS.route) },
            )
        }
        composable(TopLevelDestination.SCAN.route) {
            ScanScreen(
                onRunProfile = { id -> navController.navigate("tasks/run/$id") },
                onOpenApdu = { navController.navigate(APDU_ROUTE) },
            )
        }
        composable(TopLevelDestination.WRITE.route) { WriteScreen() }
        composable(TopLevelDestination.TASKS.route) {
            TasksScreen(
                onEdit = { id -> navController.navigate("tasks/edit/$id") },
                onRun = { id -> navController.navigate("tasks/run/$id") },
            )
        }
        composable(EDIT_ROUTE, arguments = idArg) { entry ->
            ProfileEditorScreen(profileId = entry.arguments?.getLong("id") ?: 0L, onDone = { navController.popBackStack() })
        }
        composable(RUN_ROUTE, arguments = idArg) { entry ->
            TaskRunScreen(profileId = entry.arguments?.getLong("id") ?: 0L, onDone = { navController.popBackStack() })
        }
        composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
        composable(TopLevelDestination.SETTINGS.route) { SettingsScreen() }
        composable(APDU_ROUTE) { ApduConsoleScreen(onDone = { navController.popBackStack() }) }
    }
}
