package com.cointrail.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.AmountText
import com.cointrail.ui.components.BudgetProgressSection
import com.cointrail.ui.components.ExpenseRow
import com.cointrail.ui.components.timeFormat
import com.cointrail.ui.theme.CoinTrailTheme
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
 * Home (SPEC §6.11): the month-to-date hero with its month-over-month move and the month's daily
 * trail, the today block and the budget section — or a first-run call to action when there are no
 * expenses at all.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onAddClick: () -> Unit,
    onExpenseClick: (String) -> Unit,
    onSeeAllToday: () -> Unit,
    onBudgetsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LightStatusBarIconsWhileShown()
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        MonthHero(state)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = -SHEET_OVERLAP)
                .background(MaterialTheme.colorScheme.background, SHEET_SHAPE)
                .padding(top = 20.dp, bottom = 96.dp),
        ) {
            if (!state.hasAnyExpenses) {
                FirstRunCallToAction(onAddClick, modifier = Modifier.fillMaxWidth())
            } else {
                TodayBlock(state = state, onExpenseClick = onExpenseClick, onSeeAllToday = onSeeAllToday)
                if (state.budgets.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    BudgetProgressSection(
                        title = "Budgets",
                        budgets = state.budgets,
                        titleStyle = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable(onClickLabel = "Edit budgets", onClick = onBudgetsClick),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthHero(state: HomeUiState) {
    val forest = CoinTrailTheme.forest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(forest.container)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 28.dp + SHEET_OVERLAP),
    ) {
        Text(
            text = state.month.format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            color = forest.contentMuted,
        )
        AmountText(
            amount = state.monthTotal,
            style = MaterialTheme.typography.displayMedium,
            color = forest.content,
            modifier = Modifier.semantics { contentDescription = "Spent this month ${state.monthTotal.format()}" },
        )
        val momDelta = state.momDelta
        val momPercent = state.momPercent
        if (momDelta != null && momPercent != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = describeMonthChange(momDelta, momPercent, state.month.minusMonths(1).format(MONTH_FORMAT)),
                style = MaterialTheme.typography.bodyLarge,
                color = if (momDelta.paisa > 0) forest.spendUp else forest.spendDown,
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        MonthTrail(month = state.month, trail = state.monthTrail, daysInMonth = state.daysInMonth)
    }
}

/**
 * The month as a trail: one bar per day so far, scaled to the month's biggest day, with today lit
 * and the days still ahead as faint dots.
 */
@Composable
private fun MonthTrail(month: YearMonth, trail: List<Money>, daysInMonth: Int) {
    val forest = CoinTrailTheme.forest
    val biggestDay = trail.maxOfOrNull { it.paisa }?.takeIf { it > 0 } ?: 1L
    val trailDescription = remember(month, trail) {
        val busiestIndex = trail.indices.maxByOrNull { trail[it].paisa }
        if (busiestIndex == null || trail[busiestIndex].paisa == 0L) {
            "No spending yet this month"
        } else {
            val busiestDate = month.atDay(busiestIndex + 1).format(DAY_FORMAT)
            "Daily spending this month. Biggest day $busiestDate, ${trail[busiestIndex].format()}"
        }
    }
    Column(modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = trailDescription }) {
        Canvas(modifier = Modifier.fillMaxWidth().height(64.dp)) {
            val slotWidth = size.width / daysInMonth
            val barWidth = (slotWidth * 0.58f).coerceAtMost(10.dp.toPx())
            val stubHeight = 3.dp.toPx()
            val dotRadius = 1.6.dp.toPx()
            for (dayIndex in 0 until daysInMonth) {
                val centerX = slotWidth * dayIndex + slotWidth / 2
                if (dayIndex >= trail.size) {
                    drawCircle(forest.trailAhead, dotRadius, Offset(centerX, size.height - dotRadius))
                    continue
                }
                val isToday = dayIndex == trail.lastIndex
                val barHeight = (size.height * trail[dayIndex].paisa / biggestDay).coerceAtLeast(stubHeight)
                drawRoundRect(
                    color = if (isToday) forest.trailToday else forest.trailBar,
                    topLeft = Offset(centerX - barWidth / 2, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "1", style = MaterialTheme.typography.labelSmall, color = forest.contentMuted)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "$daysInMonth", style = MaterialTheme.typography.labelSmall, color = forest.contentMuted)
        }
    }
}

@Composable
private fun TodayBlock(
    state: HomeUiState,
    onExpenseClick: (String) -> Unit,
    onSeeAllToday: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Today", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = if (state.todayCount == 0) "Nothing logged yet" else entryCount(state.todayCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AmountText(amount = state.todayTotal, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (state.todayCount == 0) {
            Text(
                text = "Tap + to log an expense.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            state.recent.forEach { row ->
                ExpenseRow(row = row, onClick = { onExpenseClick(row.id) })
            }
            TextButton(
                onClick = onSeeAllToday,
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(if (state.todayCount > state.recent.size) "See all ${state.todayCount}" else "Open today")
            }
        }
    }
}

@Composable
private fun FirstRunCallToAction(onAddClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(text = "Start your trail", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Log what you spend and this page fills in: the month's total, each day's spending and your budgets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(onClick = onAddClick) { Text("Log your first expense") }
    }
}

/**
 * The forest hero runs behind the status bar, so its icons must be light while Home is on screen,
 * whatever the system theme; leaving Home hands them back to the theme's default.
 */
@Composable
private fun LightStatusBarIconsWhileShown() {
    val view = LocalView.current
    val darkTheme = isSystemInDarkTheme()
    if (view.isInEditMode) return
    DisposableEffect(view, darkTheme) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.isAppearanceLightStatusBars = false
        onDispose { controller?.isAppearanceLightStatusBars = !darkTheme }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private val SHEET_OVERLAP = 24.dp
private val SHEET_SHAPE = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)
