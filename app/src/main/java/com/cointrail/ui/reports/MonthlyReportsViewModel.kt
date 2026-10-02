package com.cointrail.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetProgressCalculator
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.reports.CategoryComparison
import com.cointrail.domain.reports.MonthComparison
import com.cointrail.domain.reports.MonthComparisonCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
 * per-category breakdown, the month-over-month comparison and the budget bars. The viewed month is
 * held here so it survives a trip into the edit screen and back. Tapping a day navigates to the
 * shared Day screen (SPEC §6.1) — the navigation lives in the route, not here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportsViewModel(
    private val expenses: ExpenseStore,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
    private val budgets: BudgetStore,
    initialMonth: YearMonth = YearMonth.now(),
) : ViewModel() {

    private val month = MutableStateFlow(initialMonth)

    val state: StateFlow<MonthlyReportsUiState> = combine(
        monthData(),
        categories.observeAll(),
        paymentMethods.observeAll(),
        budgets.observeAll(),
    ) { monthData, categoryList, paymentMethodList, budgetList ->
        buildState(monthData, categoryList, paymentMethodList, budgetList)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MonthlyReportsUiState(initialMonth))

    fun showPreviousMonth() = changeMonth(-1)

    fun showNextMonth() = changeMonth(1)

    private fun changeMonth(delta: Long) {
        month.value = month.value.plusMonths(delta)
    }

    private fun monthData(): Flow<MonthData> = month.flatMapLatest { current ->
        val monthStart = current.atDay(1).atStartOfDay()
        val monthEnd = monthStart.plusMonths(1)
        combine(
            expenses.observeDailyTotals(current),
            expenses.observeCategoryTotals(monthStart, monthEnd),
            expenses.observeCategoryTotals(monthStart.minusMonths(1), monthStart),
        ) { dailyTotals, currentTotals, previousTotals ->
            MonthData(current, dailyTotals, MonthComparisonCalculator.compare(currentTotals, previousTotals))
        }
    }

    private fun buildState(
        monthData: MonthData,
        categoryList: List<Category>,
        paymentMethodList: List<PaymentMethod>,
        budgetList: List<Budget>,
    ): MonthlyReportsUiState {
        val categoryNames = categoryList.associate { it.id to it.name }
        val comparison = monthData.comparison
        return MonthlyReportsUiState(
            month = monthData.month,
            dailyTotals = monthData.dailyTotals.associate { it.day to it.total },
            total = comparison.currentTotal,
            previousTotal = comparison.previousTotal,
            delta = comparison.delta,
            breakdown = breakdown(comparison.categories, comparison.currentTotal, categoryNames),
            comparisons = comparison.categories.map { row ->
                CategoryComparisonRow(
                    categoryId = row.categoryId,
                    name = categoryNames[row.categoryId] ?: row.categoryId,
                    current = row.current,
                    previous = row.previous,
                    delta = row.delta,
                )
            },
            budgets = BudgetProgressCalculator.forMonth(
                budgets = budgetList,
                categories = categoryList,
                overallSpent = comparison.currentTotal,
                spentByCategory = comparison.categories.associate { it.categoryId to it.current },
                categoryNames = categoryNames,
            ),
        )
    }

    private fun breakdown(
        comparisons: List<CategoryComparison>,
        total: Money,
        categoryNames: Map<String, String>,
    ): List<CategoryBreakdownRow> {
        val spending = comparisons
            .filter { it.current > Money.ZERO }
            .sortedWith(compareByDescending<CategoryComparison> { it.current.paisa }.thenBy { it.categoryId })
        val biggest = spending.maxOfOrNull { it.current.paisa } ?: 0L
        return spending.map { row ->
            CategoryBreakdownRow(
                categoryId = row.categoryId,
                name = categoryNames[row.categoryId] ?: row.categoryId,
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

    private data class MonthData(
        val month: YearMonth,
        val dailyTotals: List<DailyTotal>,
        val comparison: MonthComparison,
    )
}
