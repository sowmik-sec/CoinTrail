package com.cointrail.ui.navigation

import com.cointrail.ui.settings.CatalogKind
import java.time.LocalDate

/**
 * Every screen in the app (SPEC §6.12). The three top-level tabs live in their own nested nav
 * graphs so each keeps its own back stack; shared drill-ins (quick-add, Day, edit) sit at the
 * root because any tab can open them.
 */
sealed class Destination(open val route: String) {
    data object Home : Destination("home")
    data object Monthly : Destination("monthly")
    data object Settings : Destination("settings")

    data object QuickAdd : Destination("quick_add")

    data class Day(val date: LocalDate) : Destination("day/$date")
    data class Edit(val expenseId: String) : Destination("edit/$expenseId")

    data object Budgets : Destination("budgets")
    data class ManageCatalog(val kind: CatalogKind) : Destination("manage_catalog/${kind.name}")
    data object Reminder : Destination("reminder")
    data object Recurring : Destination("recurring")
    data object Account : Destination("account")
    data object Backup : Destination("backup")
    data object CsvExport : Destination("csv_export")

    companion object {
        const val HOME_ROOT: String = "home-root"
        const val MONTHLY_ROOT: String = "monthly-root"
        const val SETTINGS_ROOT: String = "settings-root"

        const val DAY_PATTERN: String = "day/{date}"
        const val EDIT_PATTERN: String = "edit/{expenseId}"
        const val MANAGE_CATALOG_PATTERN: String = "manage_catalog/{kind}"
    }
}
