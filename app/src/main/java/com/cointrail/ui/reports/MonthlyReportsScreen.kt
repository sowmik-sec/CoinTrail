package com.cointrail.ui.reports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.di.AppContainer
import com.cointrail.domain.reports.MonthGrid
import com.cointrail.ui.components.BudgetProgressSection
import com.cointrail.ui.components.deltaColor
import com.cointrail.ui.components.formatDelta
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthlyReportsRoute(
    container: AppContainer,
    onDayClick: (LocalDate) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewModel: MonthlyReportsViewModel = viewModel(
        factory = remember(container) { monthlyReportsViewModelFactory(container) },
    )
    val state by viewModel.state.collectAsState()

    MonthlyReportsScreen(
        state = state,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSelectDay = onDayClick,
        onClose = onClose,
        modifier = modifier,
    )
}

private fun monthlyReportsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        MonthlyReportsViewModel(
            expenses = container.expenses,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
            budgets = container.budgets,
        )
    }
}

/**
 * The monthly reports screen (SPEC §6.4): the month calendar heatmap, the month total and
 * per-category breakdown, the month-over-month comparison and the budget bars; tapping a day
 * opens the Day screen (SPEC §6.1) for that date.
 */
@Composable
fun MonthlyReportsScreen(
    state: MonthlyReportsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MonthOverviewScreen(
        state = state,
        onPreviousMonth = onPreviousMonth,
        onNextMonth = onNextMonth,
        onSelectDay = onSelectDay,
        onClose = onClose,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonthOverviewScreen(
    state: MonthlyReportsUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (LocalDate) -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(state.month.format(MONTH_FORMAT)) },
                navigationIcon = {
                    if (onClose != null) {
                        IconButton(onClick = { onClose() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onPreviousMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                    }
                    IconButton(onClick = onNextMonth) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            MonthTotal(month = state.month, total = state.total)
            HorizontalDivider()
            MonthCalendar(month = state.month, dailyTotals = state.dailyTotals, onSelectDay = onSelectDay)
            if (state.budgets.isNotEmpty()) {
                HorizontalDivider()
                BudgetProgressSection(title = "This month's budgets", budgets = state.budgets)
            }
            HorizontalDivider()
            CategoryBreakdownSection(rows = state.breakdown, total = state.total)
            HorizontalDivider()
            MonthComparisonSection(state = state)
        }
    }
}

@Composable
private fun MonthTotal(month: YearMonth, total: Money) {
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
    }
}

private val WEEKDAY_LABELS: List<String> = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")

@Composable
private fun MonthCalendar(
    month: YearMonth,
    dailyTotals: Map<LocalDate, Money>,
    onSelectDay: (LocalDate) -> Unit,
) {
    val cells = remember(month) { MonthGrid.cells(month) }
    val maxTotal = remember(dailyTotals) {
        dailyTotals.values.maxOfOrNull { it.paisa }?.let { Money(it) } ?: Money.ZERO
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
            }
        }
        cells.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        total = date?.let { dailyTotals[it] } ?: Money.ZERO,
                        maxTotal = maxTotal,
                        onClick = onSelectDay,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    total: Money,
    maxTotal: Money,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Spacer(modifier = modifier.height(CELL_HEIGHT))
        return
    }
    val hasSpending = total.paisa > 0
    val fraction = if (maxTotal.paisa > 0) total.paisa.toFloat() / maxTotal.paisa else 0f
    val container = if (hasSpending) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f + 0.65f * fraction)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }
    Column(
        modifier = modifier
            .height(CELL_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable { onClick(date) }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hasSpending) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (hasSpending) {
            Text(
                text = total.format(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private enum class BreakdownStyle { BARS, DONUT }

@Composable
private fun CategoryBreakdownSection(rows: List<CategoryBreakdownRow>, total: Money) {
    var style by rememberSaveable { mutableStateOf(BreakdownStyle.BARS) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "By category", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            BreakdownStyle.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = style == option,
                    onClick = { style = option },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = BreakdownStyle.entries.size),
                    icon = {},
                ) {
                    Text(if (option == BreakdownStyle.BARS) "Bars" else "Donut")
                }
            }
        }
        when {
            rows.isEmpty() -> Text(
                text = "No spending this month yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            style == BreakdownStyle.BARS -> CategoryBars(rows)

            else -> CategoryDonut(rows, total)
        }
    }
}

@Composable
private fun MonthComparisonSection(state: MonthlyReportsUiState) {
    val previousMonth = state.month.minusMonths(1).format(MONTH_FORMAT)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = "Compared with $previousMonth", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Total",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatDelta(state.delta),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = deltaColor(state.delta),
            )
        }
        HorizontalDivider()
        if (state.comparisons.isEmpty()) {
            Text(
                text = "Nothing spent in either month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.comparisons.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = row.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "was ${row.previous.format()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatDelta(row.delta),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = deltaColor(row.delta),
                    )
                }
            }
        }
    }
}

private val CELL_HEIGHT = 56.dp
private const val DAYS_PER_WEEK = 7
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
