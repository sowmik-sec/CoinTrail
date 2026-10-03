package com.cointrail.domain.budget

import com.cointrail.core.Money

/** The two budget threshold crossings that raise a notification (SPEC §6.5). */
enum class BudgetAlertLevel {
    /** Spending reached 80% of the limit. */
    WARNING,

    /** Spending reached 100% of the limit. */
    EXCEEDED,
}

/**
 * Identifies a threshold that has already been alerted for one scope in a given month (Q48). The
 * month lives in the alert store's keying; the scope — not the budget row's id — lives here, so a
 * mid-month budget change (a new limit, a new override row) can never re-arm a threshold that
 * already fired for that scope and month.
 */
data class BudgetAlertKey(val scopeKey: String, val level: BudgetAlertLevel)

data class BudgetAlert(
    val scopeKey: String,
    val label: String,
    val level: BudgetAlertLevel,
    val limit: Money,
    val spent: Money,
    val percent: Int,
)

/**
 * Decides which threshold notifications are still owed for the current month, given the month's
 * progress and the thresholds already alerted. Budgets are evaluated independently; each threshold
 * fires at most once per scope per month.
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

            if (BudgetAlertKey(item.scopeKey, reached) in alreadyFired) return@mapNotNull null

            BudgetAlert(
                scopeKey = item.scopeKey,
                label = item.label,
                level = reached,
                limit = item.limit,
                spent = item.spent,
                percent = item.percent,
            )
        }
}
