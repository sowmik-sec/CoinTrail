package com.cointrail.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.data.repo.CatalogKind
import com.cointrail.data.repo.CatalogStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.DailyTotal
import com.cointrail.ui.components.ExpenseRowUi
import com.cointrail.ui.components.namesById
import com.cointrail.ui.components.toRow
import com.cointrail.ui.month.MonthStandingSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Home state (SPEC §6.11): the month-to-date hero with its month-over-month move, the today block
 * (total, entry count and the last few entries), the budget bars and the first-run flag. The
 * month's numbers — total, move, trail and budget bars — come from the one [MonthStandingSource],
 * the same answer the Monthly report reads.
 */
data class HomeUiState(
    val month: YearMonth = YearMonth.now(),
    val monthTotal: Money = Money.ZERO,
    val momDelta: Money? = null,
    val momPercent: Int? = null,
    val todayTotal: Money = Money.ZERO,
    val todayCount: Int = 0,
    val recent: List<ExpenseRowUi> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
    val hasAnyExpenses: Boolean = false,
    /** Spend for each day of the month from the 1st through today, quiet days as zero. */
    val monthTrail: List<Money> = emptyList(),
    val daysInMonth: Int = 30,
)

/** Backs the Home screen: one composed snapshot of where the month and the day stand. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val expenses: ExpenseStore,
    catalog: CatalogStore,
    monthStanding: MonthStandingSource,
    budgetAlerts: BudgetAlertTracker,
    private val today: () -> LocalDate = { LocalDate.now() },
    timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
) : ViewModel() {

    private val day = MutableStateFlow(today())

    val state: StateFlow<HomeUiState> = day.flatMapLatest { date ->
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val todayBlock: Flow<Pair<List<Expense>, Money>> =
            combine(
                expenses.observeBetween(dayStart, dayEnd),
                expenses.observeTotalBetween(dayStart, dayEnd),
            ) { rows, total -> rows to total }
        val names: Flow<Pair<Map<String, String>, Map<String, String>>> = combine(
            catalog.observeAll(CatalogKind.CATEGORIES),
            catalog.observeAll(CatalogKind.PAYMENT_METHODS),
        ) { categories, paymentMethods -> categories.namesById() to paymentMethods.namesById() }
        combine(
            monthStanding.observe(YearMonth.from(date)),
            todayBlock,
            names,
            expenses.observeHasAny(),
        ) { standing, (dayRows, dayTotal), (categoryNames, paymentMethodNames), hasAny ->
            val summary = standing.summary
            HomeUiState(
                month = YearMonth.from(date),
                monthTotal = standing.total,
                momDelta = summary.delta,
                momPercent = summary.percentChange,
                todayTotal = dayTotal,
                todayCount = dayRows.size,
                recent = dayRows
                    .take(RECENT_LIMIT)
                    .map { expense -> expense.toRow(categoryNames, paymentMethodNames, timeFormat) },
                budgets = standing.budgets,
                hasAnyExpenses = hasAny,
                monthTrail = monthTrail(date, standing.perDay),
                daysInMonth = date.lengthOfMonth(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    init {
        // Every change to month-to-date spending is a chance to cross a budget threshold.
        viewModelScope.launch {
            state.collect { ui -> budgetAlerts.evaluate(ui.budgets) }
        }
    }

    /** Re-anchors the screen to the current local day, e.g. when the app returns to the foreground. */
    fun refresh() {
        day.value = today()
    }

    /** One total per day from the 1st through [date]; days without spending read zero. */
    private fun monthTrail(date: LocalDate, dailyTotals: List<DailyTotal>): List<Money> {
        val totalByDay = dailyTotals.associate { it.day to it.total }
        return (1..date.dayOfMonth).map { dayOfMonth ->
            totalByDay[date.withDayOfMonth(dayOfMonth)] ?: Money.ZERO
        }
    }

    private companion object {
        const val RECENT_LIMIT = 5
    }
}
