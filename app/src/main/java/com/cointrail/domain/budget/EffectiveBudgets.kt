package com.cointrail.domain.budget

import com.cointrail.domain.model.Budget
import java.time.YearMonth

/**
 * The one domain decision of which budgets govern a given month (SPEC §6.5). Progress bars and
 * threshold planning read their limits through [forMonth]; no consumer builds them from the raw
 * budget list. Storage stays dumb — resolution is pure and takes the rows as they are.
 */
object EffectiveBudgets {

    /**
     * The budgets in force for [month], at most one per scope, tombstones excluded. Input order is
     * preserved so the caller decides display order.
     */
    fun forMonth(budgets: List<Budget>, month: YearMonth): List<Budget> =
        budgets.filter { it.deletedAt == null }
}
