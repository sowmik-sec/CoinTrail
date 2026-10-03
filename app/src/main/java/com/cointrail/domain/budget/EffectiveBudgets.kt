package com.cointrail.domain.budget

import com.cointrail.domain.model.Budget
import java.time.YearMonth

/**
 * The one domain decision of which budgets govern a given month (SPEC §6.5): each scope's
 * **effective budget** is its override for that month if it has one, else its default budget; an
 * explicit "no budget" override leaves the month with nothing. Progress bars and threshold planning
 * read their limits through [forMonth]; no consumer builds them from the raw budget list. Storage
 * stays dumb — resolution is pure and takes the rows as they are.
 */
object EffectiveBudgets {

    /**
     * The budgets in force for [month], at most one per scope, tombstones excluded. Scopes whose
     * effective budget is "no budget" are left out entirely — there is nothing to measure against.
     * Input order is preserved so the caller decides display order.
     */
    fun forMonth(budgets: List<Budget>, month: YearMonth): List<Budget> {
        val live = budgets.filter { !it.isDeleted }
        val defaults = live.filter { it.isDefault }.associateBy { it.scopeKey }
        val overrides = live.filter { it.month == month }.associateBy { it.scopeKey }
        return (defaults.keys + overrides.keys).mapNotNull { scope ->
            val override = overrides[scope]
            when {
                override != null -> override.takeIf { !it.isNoBudget }
                else -> defaults[scope]
            }
        }
    }
}
