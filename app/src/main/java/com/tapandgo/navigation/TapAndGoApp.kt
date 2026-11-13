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
private const val WORKFLOWS_ROUTE = "tasks/workflows"
private const val WORKFLOW_EDIT_ROUTE = "tasks/workflows/{id}"
private const val NDEF_LAB_ROUTE = "tools/ndef-lab"
private const val PREMIUM_ROUTE = "tools/premium"
private const val EMULATION_ROUTE = "tools/emulation"
private const val ROOT_ROUTE = "tools/root"
private const val WORKFLOW_TRIGGERS_ROUTE = "workflow-triggers/{id}"
private const val WORKFLOW_LAUNCH_ROUTE = "workflow-launch?token={token}&source={source}"
private const val LIBRARY_DETAIL_ROUTE = "tools/library/{id}"

private fun titleFor(route: String?): Int = when {
    route == null -> R.string.app_name
    route.startsWith("read/apdu") -> R.string.apdu_title
    route == WORKFLOWS_ROUTE -> R.string.wf_title
    route == WORKFLOW_EDIT_ROUTE -> R.string.wf_edit_title
    route == WORKFLOW_TRIGGERS_ROUTE -> R.string.trg_title
    route == NDEF_LAB_ROUTE -> R.string.lab_title
    route == PREMIUM_ROUTE -> R.string.prem_title
    route == EMULATION_ROUTE -> R.string.emu_title
    route == ROOT_ROUTE -> R.string.root_title
    route == WORKFLOW_LAUNCH_ROUTE -> R.string.run_title
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
fun TapAndGoApp(
    pendingLaunch: com.tapandgo.automation.LaunchRequest? = null,
    onLaunchConsumed: () -> Unit = {},
    pendingDeepLink: android.content.Intent? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val wide = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() } >= 600.dp
    val topLevel = TopLevelDestination.entries.any { it.route == route }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val appViewModel: AppViewModel = androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel()

    // tapandgo:// link delivered to the already-running activity (singleTop).
    LaunchedEffect(pendingDeepLink) {
        val intent = pendingDeepLink ?: return@LaunchedEffect
        onDeepLinkConsumed()
        navController.handleDeepLink(intent)
    }

    // A task tag, shortcut, tile or other app delivered a trigger token.
    LaunchedEffect(pendingLaunch) {
        val request = pendingLaunch ?: return@LaunchedEffect
        onLaunchConsumed()
        navController.navigate(appViewModel.routeFor(request))
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
        composable(TopLevelDestination.READ.route, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://read" })) {
            ScanScreen(
                onRunProfile = { id, auto -> navController.navigate("tasks/run/$id?auto=$auto") },
                onOpenApdu = { navController.navigate(APDU_ROUTE) },
            )
        }
        composable(TopLevelDestination.WRITE.route, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://write" })) { WriteScreen() }
        composable(WORKFLOWS_ROUTE, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://workflows" })) {
            com.tapandgo.feature.automation.WorkflowsScreen(
                onEdit = { id -> navController.navigate("tasks/workflows/$id") },
                onTriggers = { id -> navController.navigate("workflow-triggers/$id") },
                onOpenPremium = { navController.navigate(PREMIUM_ROUTE) },
            )
        }
        composable(WORKFLOW_TRIGGERS_ROUTE, arguments = listOf(idArg)) {
            com.tapandgo.feature.automation.WorkflowTriggersScreen(onOpenPremium = { navController.navigate(PREMIUM_ROUTE) }, onWriteTag = {
                navController.navigate(TopLevelDestination.WRITE.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            })
        }
        composable(
            WORKFLOW_LAUNCH_ROUTE,
            arguments = listOf(
                navArgument("token") { type = NavType.StringType; defaultValue = "" },
                navArgument("source") { type = NavType.StringType; defaultValue = "EXTERNAL" },
            ),
        ) { com.tapandgo.feature.automation.WorkflowLaunchScreen(onDone = { navController.popBackStack() }) }
        composable(WORKFLOW_EDIT_ROUTE, arguments = listOf(idArg)) { com.tapandgo.feature.automation.WorkflowEditorScreen(onDone = { navController.popBackStack() }) }
        composable(TopLevelDestination.TOOLS.route, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://tools" })) {
            com.tapandgo.feature.tools.ToolsScreen(
                onTagOperations = { navController.navigate("tools/ops") },
                onHistory = { navController.navigate(HISTORY_ROUTE) },
                onLibrary = { navController.navigate(LIBRARY_ROUTE) },
                onDeveloper = { navController.navigate(APDU_ROUTE) },
                onNdefLab = { navController.navigate(NDEF_LAB_ROUTE) },
                onPremium = { navController.navigate(PREMIUM_ROUTE) },
                onEmulation = { navController.navigate(EMULATION_ROUTE) },
                onRoot = { navController.navigate(ROOT_ROUTE) },
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
        composable(LIBRARY_ROUTE, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://library" })) { com.tapandgo.feature.library.LibraryScreen(onOpen = { id -> navController.navigate("tools/library/$id") }) }
        composable(LIBRARY_DETAIL_ROUTE, arguments = listOf(idArg)) {
            com.tapandgo.feature.library.LibraryDetailScreen(
                onRestore = { id -> navController.navigate("tools/ops?restore=$id") },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(ABOUT_ROUTE) { com.tapandgo.feature.tools.AboutScreen() }
        composable(TopLevelDestination.TASKS.route, deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "tapandgo://tasks" })) {
            TasksScreen(
                onWorkflows = { navController.navigate(WORKFLOWS_ROUTE) },
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
        composable(NDEF_LAB_ROUTE) {
            com.tapandgo.feature.premium.PremiumGate(com.tapandgo.billing.PremiumFeature.RAW_NDEF_EDITOR, onOpenPremium = { navController.navigate(PREMIUM_ROUTE) }) {
                com.tapandgo.feature.advanced.NdefLabScreen(onWrite = {
                    navController.navigate(TopLevelDestination.WRITE.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                    }
                })
            }
        }
        composable(PREMIUM_ROUTE) { com.tapandgo.feature.premium.PremiumScreen() }
        composable(ROOT_ROUTE) { com.tapandgo.feature.root.RootManagerScreen() }
        composable(EMULATION_ROUTE) {
            com.tapandgo.feature.premium.PremiumGate(com.tapandgo.billing.PremiumFeature.NDEF_EMULATION, onOpenPremium = { navController.navigate(PREMIUM_ROUTE) }) {
                com.tapandgo.feature.emulation.EmulationScreen()
            }
        }
        composable(SETTINGS_ROUTE) { SettingsScreen() }
        composable(APDU_ROUTE) { ApduConsoleScreen(onDone = { navController.popBackStack() }) }
    }
}
