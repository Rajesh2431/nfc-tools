package com.tapandgo.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tapandgo.R
import com.tapandgo.feature.advanced.ApduConsoleScreen
import com.tapandgo.feature.history.HistoryScreen
import com.tapandgo.feature.other.OtherScreen
import com.tapandgo.feature.scanner.ScanScreen
import com.tapandgo.feature.settings.SettingsScreen
import com.tapandgo.feature.tasks.ProfileEditorScreen
import com.tapandgo.feature.tasks.TaskRunScreen
import com.tapandgo.feature.tasks.TasksScreen
import com.tapandgo.feature.writer.WriteScreen

private const val EDIT_ROUTE = "tasks/edit/{id}"
private const val RUN_ROUTE = "tasks/run/{id}?auto={auto}"
private const val APDU_ROUTE = "read/apdu"
private const val SETTINGS_ROUTE = "settings"

private fun titleFor(route: String?): Int = when {
    route == null -> R.string.app_name
    route.startsWith("read/apdu") -> R.string.apdu_title
    route.startsWith("tasks/edit") -> R.string.tasks_edit_title
    route.startsWith("tasks/run") -> R.string.run_title
    route == SETTINGS_ROUTE -> R.string.nav_settings
    else -> TopLevelDestination.entries.firstOrNull { it.route == route }?.labelRes ?: R.string.app_name
}

/**
 * App shell: top app bar (title, back, Settings), bottom navigation on phones
 * and a navigation rail on wide screens (>= 600 dp).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapAndGoApp(pendingTaskToken: String? = null, onTaskTokenConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val topLevel = TopLevelDestination.entries.any { it.route == route }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val appViewModel: AppViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()

    // A task tag scanned while the app was not on screen (NDEF_DISCOVERED intent).
    LaunchedEffect(pendingTaskToken) {
        val token = pendingTaskToken ?: return@LaunchedEffect
        onTaskTokenConsumed()
        val profile = appViewModel.profileForToken(token)
        if (profile != null) navController.navigate("tasks/run/${profile.id}?auto=${profile.autoRun}")
    }

    fun isSelected(d: TopLevelDestination) = route == d.route || route?.startsWith(d.route + "/") == true
    fun go(d: TopLevelDestination) {
        if (d == TopLevelDestination.READ) {
            // Start destination: restoreState would bring back another tab's stack.
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
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(titleFor(route))) },
                navigationIcon = {
                    if (!topLevel && route != null) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                        }
                    }
                },
                actions = {
                    if (route != SETTINGS_ROUTE) {
                        IconButton(onClick = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
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
    val idArg = navArgument("id") { type = NavType.LongType }
    NavHost(navController = navController, startDestination = TopLevelDestination.READ.route, modifier = modifier) {
        composable(TopLevelDestination.READ.route) {
            ScanScreen(
                onRunProfile = { id, auto -> navController.navigate("tasks/run/$id?auto=$auto") },
                onOpenApdu = { navController.navigate(APDU_ROUTE) },
            )
        }
        composable(TopLevelDestination.WRITE.route) { WriteScreen() }
        composable(TopLevelDestination.OTHER.route) { OtherScreen() }
        composable(TopLevelDestination.TASKS.route) {
            TasksScreen(
                onEdit = { id -> navController.navigate("tasks/edit/$id") },
                onRun = { id -> navController.navigate("tasks/run/$id?auto=false") },
            )
        }
        composable(EDIT_ROUTE, arguments = listOf(idArg)) { entry ->
            ProfileEditorScreen(
                profileId = entry.arguments?.getLong("id") ?: 0L,
                onDone = { navController.popBackStack() },
                onWriteTag = {
                    navController.navigate(TopLevelDestination.WRITE.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(RUN_ROUTE, arguments = listOf(idArg, navArgument("auto") { type = NavType.BoolType; defaultValue = false })) { entry ->
            TaskRunScreen(
                profileId = entry.arguments?.getLong("id") ?: 0L,
                autoStarted = entry.arguments?.getBoolean("auto") ?: false,
                onDone = { navController.popBackStack() },
            )
        }
        composable(TopLevelDestination.HISTORY.route) { HistoryScreen() }
        composable(SETTINGS_ROUTE) { SettingsScreen() }
        composable(APDU_ROUTE) { ApduConsoleScreen(onDone = { navController.popBackStack() }) }
    }
}
