package com.cointrail.ui

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cointrail.di.AppContainer
import com.cointrail.ui.backup.BackupRoute
import com.cointrail.ui.day.DayRoute
import com.cointrail.ui.edit.EditExpenseRoute
import com.cointrail.ui.export.CsvExportRoute
import com.cointrail.ui.home.HomeRoute
import com.cointrail.ui.navigation.Destination
import com.cointrail.ui.quickadd.QuickAddRoute
import com.cointrail.ui.reports.MonthlyReportsRoute
import com.cointrail.ui.settings.AccountSettingsRoute
import com.cointrail.ui.settings.BudgetSettingsRoute
import com.cointrail.ui.settings.CatalogKind
import com.cointrail.ui.settings.ManageCatalogRoute
import com.cointrail.ui.settings.RecurringSettingsRoute
import com.cointrail.ui.settings.ReminderSettingsRoute
import com.cointrail.ui.settings.SettingsRoute
import java.time.LocalDate

private data class TopLevelTab(
    val destination: Destination,
    val rootRoute: String,
    val label: String,
    val icon: ImageVector,
)

private val TOP_LEVEL_TABS = listOf(
    TopLevelTab(Destination.Home, Destination.HOME_ROOT, "Home", Icons.Filled.Home),
    TopLevelTab(Destination.Monthly, Destination.MONTHLY_ROOT, "Monthly", Icons.Filled.DateRange),
    TopLevelTab(Destination.Settings, Destination.SETTINGS_ROOT, "Settings", Icons.Filled.Settings),
)

/**
 * Root of the app's flow (SPEC §6.12): three bottom tabs — Home, Monthly, Settings — with
 * full-screen drill-ins (quick-add, edit, settings sub-screens) and the quick-add FAB on every
 * tab. Tapping the daily reminder notification arrives here with [startInQuickAdd] set, which
 * navigates straight to quick-add (SPEC §6.6).
 */
@Composable
fun CoinTrailApp(
    container: AppContainer,
    startInQuickAdd: Boolean = false,
    onStartInQuickAddConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TOP_LEVEL_TABS.any { it.destination.route == currentRoute }

    LaunchedEffect(startInQuickAdd) {
        if (startInQuickAdd) {
            navController.navigate(Destination.QuickAdd.route)
            onStartInQuickAddConsumed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        // Each screen owns the top inset: drill-ins pad it in their top bar, Home draws its hero
        // behind the status bar.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TOP_LEVEL_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.destination.route,
                            onClick = { navController.openTab(tab.rootRoute) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (isTopLevel) {
                FloatingActionButton(
                    onClick = { navController.navigate(Destination.QuickAdd.route) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add expense")
                }
            }
        },
    ) { padding ->
        AppNavHost(
            container = container,
            navController = navController,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** Standard bottom-navigation tab switch: keeps each tab's back stack and state across switches. */
private fun NavHostController.openTab(rootRoute: String) {
    navigate(rootRoute) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(
    container: AppContainer,
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.HOME_ROOT,
        modifier = modifier,
    ) {
        navigation(startDestination = Destination.Home.route, route = Destination.HOME_ROOT) {
            composable(Destination.Home.route) {
                HomeRoute(
                    container = container,
                    onAddClick = { navController.navigate(Destination.QuickAdd.route) },
                    onExpenseClick = { navController.navigate(Destination.Edit(it).route) },
                    onSeeAllToday = { navController.navigate(Destination.Day(LocalDate.now()).route) },
                    onBudgetsClick = { navController.navigate(Destination.Budgets.route) },
                )
            }
        }
        navigation(startDestination = Destination.Monthly.route, route = Destination.MONTHLY_ROOT) {
            composable(Destination.Monthly.route) {
                MonthlyReportsRoute(
                    container = container,
                    onDayClick = { navController.navigate(Destination.Day(it).route) },
                )
            }
        }
        navigation(startDestination = Destination.Settings.route, route = Destination.SETTINGS_ROOT) {
            composable(Destination.Settings.route) {
                SettingsRoute(
                    onManage = { navController.navigate(Destination.ManageCatalog(it).route) },
                    onBudgets = { navController.navigate(Destination.Budgets.route) },
                    onReminder = { navController.navigate(Destination.Reminder.route) },
                    onRecurring = { navController.navigate(Destination.Recurring.route) },
                    onExportCsv = { navController.navigate(Destination.CsvExport.route) },
                    onBackup = { navController.navigate(Destination.Backup.route) },
                    onAccount = { navController.navigate(Destination.Account.route) },
                )
            }
            composable(Destination.Budgets.route) {
                BudgetSettingsRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Destination.MANAGE_CATALOG_PATTERN,
                arguments = listOf(navArgument("kind") { type = NavType.StringType }),
            ) { entry ->
                val kind = entry.arguments?.getString("kind")?.let { CatalogKind.valueOf(it) }
                    ?: CatalogKind.CATEGORIES
                ManageCatalogRoute(
                    kind = kind,
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Reminder.route) {
                ReminderSettingsRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Recurring.route) {
                RecurringSettingsRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Account.route) {
                AccountSettingsRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.Backup.route) {
                BackupRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
            composable(Destination.CsvExport.route) {
                CsvExportRoute(
                    container = container,
                    onDone = { navController.popBackStack() },
                )
            }
        }
        composable(Destination.QuickAdd.route) {
            QuickAddRoute(
                container = container,
                onDone = { navController.popBackStack() },
            )
        }
        composable(
            route = Destination.DAY_PATTERN,
            arguments = listOf(navArgument("date") { type = NavType.StringType }),
        ) { entry ->
            val date = entry.arguments?.getString("date")?.let { LocalDate.parse(it) } ?: LocalDate.now()
            DayRoute(
                date = date,
                container = container,
                onExpenseClick = { navController.navigate(Destination.Edit(it).route) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Destination.EDIT_PATTERN,
            arguments = listOf(navArgument("expenseId") { type = NavType.StringType }),
        ) { entry ->
            EditExpenseRoute(
                expenseId = entry.arguments?.getString("expenseId").orEmpty(),
                container = container,
                onDone = { navController.popBackStack() },
            )
        }
    }
}
