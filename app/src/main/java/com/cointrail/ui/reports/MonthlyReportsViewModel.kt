package com.cointrail.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.ui.month.CategoryStanding
import com.cointrail.ui.month.MonthStanding
import com.cointrail.ui.month.MonthStandingSource
import com.cointrail.ui.month.nextMonth
import com.cointrail.ui.month.previousMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

/** One category's slice of the month, sized for the horizontal bar or donut segment. */
data class CategoryBreakdownRow(
    val categoryId: String,
    val name: String,
    val amount: Money,
    val sharePercent: Int,
    val barFraction: Float,
)

/** One category's month-over-month move, with its name resolved for display. */
data class CategoryComparisonRow(
    val categoryId: String,
    val name: String,
    val current: Money,
    val previous: Money,
    val delta: Money,
)

data class MonthlyReportsUiState(
    val month: YearMonth,
    val dailyTotals: Map<LocalDate, Money> = emptyMap(),
    val total: Money = Money.ZERO,
    val previousTotal: Money = Money.ZERO,
    val delta: Money = Money.ZERO,
    val breakdown: List<CategoryBreakdownRow> = emptyList(),
    val comparisons: List<CategoryComparisonRow> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
)

/**
 * Backs the monthly reports screen (SPEC §6.4): the calendar heatmap, the month total and
 * per-category breakdown, the month-over-month comparison and the budget bars — all read from the
 * one [MonthStandingSource] Home reads, so the two screens can no longer disagree about the same
 * month, and this screen does not resolve effective budgets or compare months itself. The viewed
 * month is held here so it survives a trip into the edit screen and back. Tapping a day navigates
 * to the shared Day screen (SPEC §6.1) — the navigation lives in the route, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportsViewModel(
    private val monthStanding: MonthStandingSource,
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel() {

    private val month = MutableStateFlow(initialMonth)

    val state: StateFlow<MonthlyReportsUiState> = month
        .flatMapLatest { viewed ->
            monthStanding.observe(viewed).map { standing -> buildState(viewed, standing) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MonthlyReportsUiState(initialMonth))

    fun showPreviousMonth() {
        month.value = month.value.previousMonth()
    }

    fun showNextMonth() {
        month.value = month.value.nextMonth()
    }

    private fun buildState(month: YearMonth, standing: MonthStanding): MonthlyReportsUiState {
        return MonthlyReportsUiState(
            month = month,
            dailyTotals = standing.perDay.associate { it.day to it.total },
            total = standing.total,
            previousTotal = standing.previousTotal,
            delta = standing.delta,
            breakdown = breakdown(standing.categories, standing.total),
            comparisons = standing.categories.map { row ->
                CategoryComparisonRow(
                    categoryId = row.categoryId,
                    name = row.name,
                    current = row.current,
                    previous = row.previous,
                    delta = row.delta,
                )
            },
            budgets = standing.budgets,
        )
    }

    private fun breakdown(categories: List<CategoryStanding>, total: Money): List<CategoryBreakdownRow> {
        val spending = categories
            .filter { it.current > Money.ZERO }
            .sortedWith(compareByDescending<CategoryStanding> { it.current.paisa }.thenBy { it.categoryId })
        val biggest = spending.maxOfOrNull { it.current.paisa } ?: 0L
        return spending.map { row ->
            CategoryBreakdownRow(
                categoryId = row.categoryId,
                name = row.name,
                amount = row.current,
                sharePercent = if (total.paisa > 0) {
                    ((row.current.paisa * 100 + total.paisa / 2) / total.paisa).toInt()
                } else {
                    0
                },
                barFraction = if (biggest > 0) row.current.paisa.toFloat() / biggest else 0f,
            )
        }
    }
}
