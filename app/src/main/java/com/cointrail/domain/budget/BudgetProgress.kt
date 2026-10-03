package com.cointrail.domain.budget

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category

/** The label shown for the overall (non-category) budget. */
const val OVERALL_BUDGET_LABEL = "Overall"

/** How a budget's month-to-date spending compares with its limit (SPEC §6.5). */
enum class BudgetStatus {
    /** Below 80% of the limit. */
    ON_TRACK,

    /** At or above 80% but below 100%. */
    WARNING,

    /** At or above 100% - the budget is spent. */
    EXCEEDED,
}

/**
 * A budget paired with the current month's spending against it. Threshold comparisons use integer
 * paisa only; [fraction] exists purely to size the progress bar.
 */
data class BudgetProgress(
    val budgetId: String,
    val categoryId: String?,
    val label: String,
    val limit: Money,
    val spent: Money,
) {
    init {
        require(limit > Money.ZERO) { "Budget limit must be positive" }
        require(spent >= Money.ZERO) { "Spent amount must not be negative" }
    }

    /** The scope this progress belongs to: its category, or the overall scope. */
    val scopeKey: String get() = categoryId ?: Budget.OVERALL_SCOPE

    val status: BudgetStatus
        get() = when {
            spent.paisa >= limit.paisa -> BudgetStatus.EXCEEDED
            spent.paisa * 100 >= limit.paisa * WARNING_PERCENT -> BudgetStatus.WARNING
            else -> BudgetStatus.ON_TRACK
        }

    /** The rounded percentage of the limit spent; may exceed 100 when overspent. */
    val percent: Int
        get() = ((spent.paisa * 100 + limit.paisa / 2) / limit.paisa).toInt()

    private companion object {
        const val WARNING_PERCENT = 80L
    }
}

object BudgetProgressCalculator {

    /**
     * Builds the progress rows for a month's spending. [budgets] are the month's effective budgets
     * as resolved by [EffectiveBudgets] — every row carries a positive limit; "no budget" scopes
     * never reach this far. The overall budget (null [Budget.categoryId]) is measured against
     * [overallSpent]; a category budget against its entry in [spentByCategory] (0 when the category
     * has no spending yet). Input order is preserved so the caller decides whether the overall bar
     * comes first.
     */
    fun calculate(
        budgets: List<Budget>,
        overallSpent: Money,
        spentByCategory: Map<String, Money>,
        labelFor: (String?) -> String = { it ?: OVERALL_BUDGET_LABEL },
    ): List<BudgetProgress> = budgets.map { budget ->
        val spent = if (budget.categoryId == null) {
            overallSpent
        } else {
            spentByCategory[budget.categoryId] ?: Money.ZERO
        }
        BudgetProgress(
            budgetId = budget.id,
            categoryId = budget.categoryId,
            label = labelFor(budget.categoryId),
            limit = requireNotNull(budget.monthlyLimit) { "Progress needs a limit-bearing budget" },
            spent = spent,
        )
    }

    /**
     * The display-ordered progress bars for a month, shared by Today and the monthly reports screen:
     * the overall budget first, then category budgets in [categories] order. [categoryNames] resolves
     * a category budget's label.
     */
    fun forMonth(
        budgets: List<Budget>,
        categories: List<Category>,
        overallSpent: Money,
        spentByCategory: Map<String, Money>,
        categoryNames: Map<String, String>,
    ): List<BudgetProgress> {
        val categoryOrder = categories.withIndex().associate { (index, category) -> category.id to index }
        return calculate(
            budgets = orderForDisplay(budgets, categoryOrder),
            overallSpent = overallSpent,
            spentByCategory = spentByCategory,
            labelFor = { categoryId -> categoryId?.let { categoryNames[it] ?: it } ?: OVERALL_BUDGET_LABEL },
        )
    }

    /**
     * Orders budgets for display: the overall budget first, then categories in [categoryOrder],
     * then any budget whose category is missing from it.
     */
    private fun orderForDisplay(budgets: List<Budget>, categoryOrder: Map<String, Int>): List<Budget> =
        budgets.sortedBy { budget ->
            budget.categoryId?.let { categoryOrder[it] ?: Int.MAX_VALUE } ?: -1
        }
}
