package com.cointrail.ui.day

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.BudgetProgressSection
import com.cointrail.ui.components.ExpenseRow
import com.cointrail.ui.components.ExpenseRowUi
import com.cointrail.ui.components.timeFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DayRoute(
    date: LocalDate,
    container: AppContainer,
    onExpenseClick: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val rowTimeFormat = remember(is24Hour) { timeFormat(is24Hour) }
    val viewModel: DayViewModel = viewModel(
        factory = remember(container, date, rowTimeFormat) { dayViewModelFactory(container, date, rowTimeFormat) },
    )
    val state by viewModel.state.collectAsState()
    val pendingUndoId by viewModel.pendingUndoId.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(pendingUndoId) {
        if (pendingUndoId == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(message = "Expense deleted", actionLabel = "Undo")
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete() else viewModel.onUndoDismissed()
    }

    DayScreen(
        state = state,
        isToday = date == LocalDate.now(),
        snackbarHostState = snackbarHostState,
        onExpenseClick = onExpenseClick,
        onDelete = viewModel::delete,
        onBack = onBack,
        date = date,
        modifier = modifier,
    )
}

private fun dayViewModelFactory(
    container: AppContainer,
    date: LocalDate,
    timeFormat: DateTimeFormatter,
) = viewModelFactory {
    initializer {
        DayViewModel(
            date = date,
            expenses = container.expenses,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
            budgets = container.budgets,
            budgetAlerts = container.budgetAlerts,
            timeFormat = timeFormat,
        )
    }
}

/**
 * The Day screen (SPEC §6.1): one date's expenses newest first with that day's total at top.
 * "Today" is the Day screen for the current date; other dates are titled with the date. Rows are
 * swipe-to-delete with an undo snackbar and tap-to-edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    state: DayUiState,
    isToday: Boolean,
    date: LocalDate,
    snackbarHostState: SnackbarHostState,
    onExpenseClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (isToday) "Today" else date.format(DAY_FORMAT)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = { onBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            DayTotal(state.total, isToday)
            if (state.budgets.isNotEmpty()) {
                HorizontalDivider()
                BudgetProgressSection("This month's budgets", state.budgets)
            }
            HorizontalDivider()
            if (state.isEmpty) {
                EmptyDay(isToday, modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    items(state.rows, key = { it.id }) { row ->
                        SwipeToDeleteRow(row = row, onClick = { onExpenseClick(row.id) }, onDelete = onDelete)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteRow(
    row: ExpenseRowUi,
    onClick: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(row.id)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = { DeleteBackground() },
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            ExpenseRow(row, onClick)
        }
    }
}

@Composable
private fun DeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = "Delete",
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun DayTotal(total: Money, isToday: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = if (isToday) "Spent today" else "Spent that day",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = total.format(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun EmptyDay(isToday: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(
                text = if (isToday) "No expenses yet today" else "No expenses logged that day.",
                style = MaterialTheme.typography.titleMedium,
            )
            if (isToday) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap + to log your first one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
