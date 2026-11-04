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
private const val OPS_ROUTE = "tools/ops?op={op}&restore={restore}"
private const val HISTORY_ROUTE = "tools/history"
private const val ABOUT_ROUTE = "tools/about"
private const val LIBRARY_ROUTE = "tools/library"
private const val LIBRARY_DETAIL_ROUTE = "tools/library/{id}"

private fun titleFor(route: String?): Int = when {
    route == null -> R.string.app_name
    route.startsWith("read/apdu") -> R.string.apdu_title
    route.startsWith("tasks/edit") -> R.string.tasks_edit_title
    route.startsWith("tasks/run") -> R.string.run_title
    route == SETTINGS_ROUTE -> R.string.nav_settings
    route.startsWith("tools/ops") -> R.string.tools_tag_ops
    route == HISTORY_ROUTE -> R.string.nav_history
    route == ABOUT_ROUTE -> R.string.about_title
    route == LIBRARY_ROUTE || route == LIBRARY_DETAIL_ROUTE -> R.string.library_title
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
        if (d == TopLevelDestination.HOME) {
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
    NavHost(navController = navController, startDestination = TopLevelDestination.HOME.route, modifier = modifier) {
        composable(TopLevelDestination.HOME.route) {
            fun tab(r: String) = navController.navigate(r) { launchSingleTop = true }
            com.tapandgo.feature.home.HomeScreen(
                com.tapandgo.feature.home.HomeActions(
                    read = { tab(TopLevelDestination.READ.route) },
                    write = { tab(TopLevelDestination.WRITE.route) },
                    erase = { navController.navigate("tools/ops?op=ERASE") },
                    copy = { navController.navigate("tools/ops?op=COPY") },
                    createTask = { navController.navigate("tasks/edit/0") },
                    tasks = { tab(TopLevelDestination.TASKS.route) },
                    runProfile = { id -> navController.navigate("tasks/run/$id?auto=false") },
                    history = { navController.navigate(LIBRARY_ROUTE) },
                    tools = { tab(TopLevelDestination.TOOLS.route) },
                ),
            )
        }
        composable(TopLevelDestination.READ.route) {
            ScanScreen(
                onRunProfile = { id, auto -> navController.navigate("tasks/run/$id?auto=$auto") },
                onOpenApdu = { navController.navigate(APDU_ROUTE) },
            )
        }
        composable(TopLevelDestination.WRITE.route) { WriteScreen() }
        composable(TopLevelDestination.TOOLS.route) {
            com.tapandgo.feature.tools.ToolsScreen(
                onTagOperations = { navController.navigate("tools/ops") },
                onHistory = { navController.navigate(HISTORY_ROUTE) },
                onLibrary = { navController.navigate(LIBRARY_ROUTE) },
                onDeveloper = { navController.navigate(APDU_ROUTE) },
                onSettings = { navController.navigate(SETTINGS_ROUTE) },
                onAbout = { navController.navigate(ABOUT_ROUTE) },
            )
        }
        composable(
            OPS_ROUTE,
            arguments = listOf(
                navArgument("op") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("restore") { type = NavType.LongType; defaultValue = 0L },
            ),
        ) { entry ->
            OtherScreen(initialOp = entry.arguments?.getString("op"), restoreId = entry.arguments?.getLong("restore"))
        }
        composable(LIBRARY_ROUTE) { com.tapandgo.feature.library.LibraryScreen(onOpen = { id -> navController.navigate("tools/library/$id") }) }
        composable(LIBRARY_DETAIL_ROUTE, arguments = listOf(idArg)) {
            com.tapandgo.feature.library.LibraryDetailScreen(
                onRestore = { id -> navController.navigate("tools/ops?restore=$id") },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(ABOUT_ROUTE) { com.tapandgo.feature.tools.AboutScreen() }
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
        composable(HISTORY_ROUTE) { HistoryScreen() }
        composable(SETTINGS_ROUTE) { SettingsScreen() }
        composable(APDU_ROUTE) { ApduConsoleScreen(onDone = { navController.popBackStack() }) }
    }
}
