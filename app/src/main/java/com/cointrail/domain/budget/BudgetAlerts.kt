package com.cointrail.domain.budget

import com.cointrail.core.Money

/** The two budget threshold crossings that raise a notification (SPEC §6.5). */
enum class BudgetAlertLevel {
    /** Spending reached 80% of the limit. */
    WARNING,

    /** Spending reached 100% of the limit. */
    EXCEEDED,
}

/** Identifies a threshold that has already been alerted for one budget in a given month. */
data class BudgetAlertKey(val budgetId: String, val level: BudgetAlertLevel)

data class BudgetAlert(
    val budgetId: String,
    val label: String,
    val level: BudgetAlertLevel,
    val limit: Money,
    val spent: Money,
    val percent: Int,
)

/**
 * Decides which threshold notifications are still owed for the current month, given the month's
 * progress and the thresholds already alerted. Budgets are evaluated independently; each threshold
 * fires at most once per budget per month.
 *
 * A budget only ever reports its highest reached level, so jumping straight past the limit raises a
 * single exceeded alert rather than a warning and an exceeded alert at once.
 */
object BudgetAlertEvaluator {

    fun plan(progress: List<BudgetProgress>, alreadyFired: Set<BudgetAlertKey>): List<BudgetAlert> =
        progress.mapNotNull { item ->
            val reached = when (item.status) {
                BudgetStatus.EXCEEDED -> BudgetAlertLevel.EXCEEDED
                BudgetStatus.WARNING -> BudgetAlertLevel.WARNING
                BudgetStatus.ON_TRACK -> null
            } ?: return@mapNotNull null

            if (BudgetAlertKey(item.budgetId, reached) in alreadyFired) return@mapNotNull null

            BudgetAlert(
                budgetId = item.budgetId,
                label = item.label,
                level = reached,
                limit = item.limit,
                spent = item.spent,
                percent = item.percent,
            )
        }
}
