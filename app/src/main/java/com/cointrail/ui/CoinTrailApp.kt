package com.cointrail.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cointrail.di.AppContainer
import com.cointrail.ui.edit.EditExpenseRoute
import com.cointrail.ui.export.CsvExportRoute
import com.cointrail.ui.quickadd.QuickAddRoute
import com.cointrail.ui.settings.BudgetSettingsRoute
import com.cointrail.ui.settings.CatalogKind
import com.cointrail.ui.settings.ManageCatalogRoute
import com.cointrail.ui.settings.SettingsRoute
import com.cointrail.ui.today.TodayRoute

/**
 * Root of the app's flow: Today (the everyday screen), quick-add, edit-expense and settings.
 * The daily reminder deep-links into quick-add later (Plan 4); for now the FAB is the way in.
 */
@Composable
fun CoinTrailApp(container: AppContainer, modifier: Modifier = Modifier) {
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showBudgets by rememberSaveable { mutableStateOf(false) }
    var showExport by rememberSaveable { mutableStateOf(false) }
    var managingKind by rememberSaveable { mutableStateOf<CatalogKind?>(null) }
    val editId = editingId

    when {
        showQuickAdd -> {
            BackHandler { showQuickAdd = false }
            QuickAddRoute(
                container = container,
                onDone = { showQuickAdd = false },
                modifier = modifier,
            )
        }

        editId != null -> {
            BackHandler { editingId = null }
            EditExpenseRoute(
                expenseId = editId,
                container = container,
                onDone = { editingId = null },
                modifier = modifier,
            )
        }

        managingKind != null -> {
            val kind = managingKind!!
            BackHandler { managingKind = null }
            ManageCatalogRoute(
                kind = kind,
                container = container,
                onDone = { managingKind = null },
                modifier = modifier,
            )
        }

        showExport -> {
            BackHandler { showExport = false }
            CsvExportRoute(
                container = container,
                onDone = { showExport = false },
                modifier = modifier,
            )
        }

        showBudgets -> {
            BackHandler { showBudgets = false }
            BudgetSettingsRoute(
                container = container,
                onDone = { showBudgets = false },
                modifier = modifier,
            )
        }

        showSettings -> {
            BackHandler { showSettings = false }
            SettingsRoute(
                onManage = { managingKind = it },
                onBudgets = { showBudgets = true },
                onExportCsv = { showExport = true },
                onClose = { showSettings = false },
                modifier = modifier,
            )
        }

        else -> {
            TodayRoute(
                container = container,
                onAddClick = { showQuickAdd = true },
                onExpenseClick = { editingId = it },
                onSettingsClick = { showSettings = true },
                modifier = modifier,
            )
        }
    }
}
