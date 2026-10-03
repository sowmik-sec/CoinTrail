package com.cointrail.data.alerts

import com.cointrail.domain.budget.BudgetAlertEvaluator
import com.cointrail.domain.budget.BudgetAlertKey
import com.cointrail.domain.budget.BudgetAlertLevel
import com.cointrail.domain.budget.BudgetProgress
import java.time.YearMonth

/**
 * Raises budget threshold notifications for the current month, at most once per scope and level
 * (Q48). The fired thresholds are read from [store] and recorded back, so repeated evaluation as
 * spending changes is safe and a threshold never fires twice in a month — including after a
 * mid-month budget change, because the bookkeeping is keyed by scope, not by the budget row that
 * happened to govern when the threshold fired. A new month starts with a clean slate and therefore
 * re-arms (SPEC §6.5).
 *
 * Only alerts the [notifier] actually delivered are recorded, so a crossing that could not be shown
 * (for example the notification permission is not yet granted) still fires once it can be.
 */
class BudgetAlertTracker(
    private val store: BudgetAlertStore,
    private val notifier: BudgetNotifier,
    private val month: () -> YearMonth = { YearMonth.now() },
) {

    suspend fun evaluate(progress: List<BudgetProgress>) {
        if (progress.isEmpty()) return

        val current = month()
        val delivered = mutableSetOf<BudgetAlertKey>()
        BudgetAlertEvaluator.plan(progress, store.firedFor(current)).forEach { alert ->
            if (!notifier.notify(alert)) return@forEach
            delivered += BudgetAlertKey(alert.scopeKey, alert.level)
            // An exceeded alert subsumes the warning for the same scope.
            if (alert.level == BudgetAlertLevel.EXCEEDED) {
                delivered += BudgetAlertKey(alert.scopeKey, BudgetAlertLevel.WARNING)
            }
        }
        if (delivered.isNotEmpty()) store.markFired(current, delivered)
    }
}
