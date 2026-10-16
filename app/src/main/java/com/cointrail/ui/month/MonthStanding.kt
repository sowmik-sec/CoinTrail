package com.cointrail.ui.month

import com.cointrail.core.Money
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CatalogKind
import com.cointrail.data.repo.CatalogStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetProgressCalculator
import com.cointrail.domain.budget.EffectiveBudgets
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.reports.MonthComparisonCalculator
import com.cointrail.domain.reports.MonthSummary
import com.cointrail.ui.components.nameOf
import com.cointrail.ui.components.namesById
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One place answers what a month amounts to (CONTEXT: month standing): its total, the previous
 * month's total and the move between them, the per-day figures, the per-category comparison with
 * names resolved, and how the month measures against that month's effective budget. Home and the
 * Monthly report both read [MonthStandingSource.observe], so the two can no longer disagree about
 * the same month. Month stepping and month naming are declared here too.
 */

/** The previous/next month, defined once for every month stepper. */
fun YearMonth.previousMonth(): YearMonth = minusMonths(1)

fun YearMonth.nextMonth(): YearMonth = plusMonths(1)

/** A month header's full label ("October 2026"), shared by the Monthly report and the budget picker. */
val MONTH_TITLE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(Locale.ENGLISH)

/** A month's bare name in prose ("October"), as Home's hero names the month. */
val MONTH_NAME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM").withLocale(Locale.ENGLISH)

/** One category's slice of a month against the month before it, with its name resolved. */
data class CategoryStanding(
    val categoryId: String,
    val name: String,
    val current: Money,
    val previous: Money,
) {
    val delta: Money get() = current - previous
}

/**
 * The answer for one month. [categories] carries the per-category comparison in the biggest-move-
 * first order the domain calculator defines; [summary] is the month's move, null-guarded the way
 * the domain defines it (null until the previous month has data).
 */
data class MonthStanding(
    val month: YearMonth,
    val total: Money,
    val previousTotal: Money,
    val perDay: List<DailyTotal>,
    val categories: List<CategoryStanding>,
    val budgets: List<BudgetProgress>,
) {
    /** The month-over-month move as the domain summarizes it (SPEC §6.11). */
    val summary: MonthSummary get() = MonthSummary(currentTotal = total, previousTotal = previousTotal)

    /** The raw move between the two totals; null-guarding for display stays with each screen's copy. */
    val delta: Money get() = total - previousTotal
}

/**
 * Reads a month's standing from the stores. Both screens observe this one source, so for one
 * month's data they receive one identical answer.
 */
class MonthStandingSource(
    private val expenses: ExpenseStore,
    catalog: CatalogStore,
    budgets: BudgetStore,
) {

    private val categories: Flow<List<CatalogItem>> = catalog.observeAll(CatalogKind.CATEGORIES)
    private val allBudgets: Flow<List<Budget>> = budgets.observeAll()

    fun observe(month: YearMonth): Flow<MonthStanding> {
        val monthStart = month.atDay(1).atStartOfDay()
        val monthEnd = monthStart.plusMonths(1)
        val previousStart = monthStart.minusMonths(1)
        val expenseFacts: Flow<ExpenseFacts> = combine(
            expenses.observeTotalBetween(monthStart, monthEnd),
            expenses.observeTotalBetween(previousStart, monthStart),
            expenses.observeCategoryTotals(monthStart, monthEnd),
            expenses.observeCategoryTotals(previousStart, monthStart),
            expenses.observeDailyTotals(month),
        ) { total, previousTotal, currentTotals, previousTotals, perDay ->
            ExpenseFacts(total, previousTotal, currentTotals, previousTotals, perDay)
        }
        return combine(expenseFacts, categories, allBudgets) { facts, categoryList, budgetList ->
            val names = categoryList.namesById()
            val comparison = MonthComparisonCalculator.compare(facts.currentTotals, facts.previousTotals)
            MonthStanding(
                month = month,
                total = facts.total,
                previousTotal = facts.previousTotal,
                perDay = facts.perDay,
                categories = comparison.categories.map { row ->
                    CategoryStanding(
                        categoryId = row.categoryId,
                        name = names.nameOf(row.categoryId),
                        current = row.current,
                        previous = row.previous,
                    )
                },
                budgets = BudgetProgressCalculator.forMonth(
                    budgets = EffectiveBudgets.forMonth(budgetList, month),
                    categories = categoryList,
                    overallSpent = facts.total,
                    spentByCategory = facts.currentTotals.associate { it.categoryId to it.total },
                    categoryNames = names,
                ),
            )
        }
    }

    private data class ExpenseFacts(
        val total: Money,
        val previousTotal: Money,
        val currentTotals: List<CategoryTotal>,
        val previousTotals: List<CategoryTotal>,
        val perDay: List<DailyTotal>,
    )
}
