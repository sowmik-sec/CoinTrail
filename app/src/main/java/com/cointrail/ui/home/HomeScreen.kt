package com.cointrail.ui.home

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.BudgetProgressSection
import com.cointrail.ui.components.ExpenseRow
import com.cointrail.ui.components.deltaColor
import com.cointrail.ui.components.formatDelta
import com.cointrail.ui.components.timeFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeRoute(
    container: AppContainer,
    onAddClick: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onSeeAllToday: () -> Unit,
    onBudgetsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val rowTimeFormat = remember(is24Hour) { timeFormat(is24Hour) }
    val viewModel: HomeViewModel = viewModel(
        factory = remember(container, rowTimeFormat) { homeViewModelFactory(container, rowTimeFormat) },
    )
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()

    HomeScreen(
        state = state,
        onAddClick = onAddClick,
        onExpenseClick = onExpenseClick,
        onSeeAllToday = onSeeAllToday,
        onBudgetsClick = onBudgetsClick,
        modifier = modifier,
    )
}

private fun homeViewModelFactory(container: AppContainer, timeFormat: DateTimeFormatter) = viewModelFactory {
    initializer {
        HomeViewModel(
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
 * Home (SPEC §6.11): the month-to-date hero with its month-over-month move, the today block and
 * the budget section — or a first-run call to action when there are no expenses at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddClick: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onSeeAllToday: () -> Unit,
    onBudgetsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("CoinTrail") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
        ) {
            MonthHero(
                month = state.month,
                total = state.monthTotal,
                momDelta = state.momDelta,
                momPercent = state.momPercent,
            )
            if (!state.hasAnyExpenses) {
                FirstRunCallToAction(onAddClick, modifier = Modifier.fillMaxWidth())
            } else {
                HorizontalDivider()
                TodayBlock(state = state, onExpenseClick = onExpenseClick, onSeeAllToday = onSeeAllToday)
                if (state.budgets.isNotEmpty()) {
                    HorizontalDivider()
                    BudgetProgressSection(
                        title = "This month's budgets",
                        budgets = state.budgets,
                        modifier = Modifier.clickable(onClick = onBudgetsClick),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHero(month: YearMonth, total: Money, momDelta: Money?, momPercent: Int?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = "Spent in ${month.format(MONTH_FORMAT)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = total.format(),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (momDelta != null && momPercent != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${formatDelta(momDelta)} (${formatPercent(momPercent)}) vs ${month.minusMonths(1).format(MONTH_FORMAT)}",
                style = MaterialTheme.typography.bodyMedium,
                color = deltaColor(momDelta),
            )
        }
    }
}

/** "+12%" when spending rose, "-13%" when it fell, "0%" when it held steady. */
private fun formatPercent(percent: Int): String = if (percent > 0) "+$percent%" else "$percent%"

@Composable
private fun TodayBlock(
    state: HomeUiState,
    onExpenseClick: (String) -> Unit,
    onSeeAllToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Today", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (state.todayCount > 0) {
                TextButton(onClick = onSeeAllToday) { Text("See all") }
            }
        }
        if (state.todayCount == 0) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(text = "No expenses yet today", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Tap + to log your first one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = state.todayTotal.format(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (state.todayCount == 1) "1 entry" else "${state.todayCount} entries",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            state.recent.forEach { row ->
                ExpenseRow(row = row, onClick = { onExpenseClick(row.id) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FirstRunCallToAction(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "No expenses yet", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Log your first expense to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onAddClick) { Text("Log your first expense") }
    }
}

private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
