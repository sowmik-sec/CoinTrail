package com.cointrail.domain.budget

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The single seam that decides which budgets govern a month (issue 14, ticket 15). Today every row
 * is a default budget, so resolution passes live rows through unchanged — Home and the Monthly
 * report read their limits through here instead of the raw budget list.
 */
class EffectiveBudgetsTest {

    private val ts: LocalDateTime = LocalDateTime.of(2026, 10, 1, 0, 0)
    private val october: YearMonth = YearMonth.of(2026, 10)

    private fun budget(id: String, categoryId: String?, paisa: Long, deletedAt: LocalDateTime? = null) =
        Budget(
            id = id,
            categoryId = categoryId,
            monthlyLimit = Money(paisa),
            updatedAt = ts,
            deletedAt = deletedAt,
        )

    @Test
    fun `every live budget governs any month while all rows are defaults`() {
        val overall = budget("overall", null, 200_000)
        val food = budget("food", "preset-food", 100_000)

        assertEquals(
            listOf(overall, food),
            EffectiveBudgets.forMonth(listOf(overall, food), october),
        )
        assertEquals(
            listOf(overall, food),
            EffectiveBudgets.forMonth(listOf(overall, food), YearMonth.of(2025, 3)),
        )
    }

    @Test
    fun `tombstoned budgets never govern a month`() {
        val live = budget("overall", null, 200_000)
        val cleared = budget("food", "preset-food", 100_000, deletedAt = ts)

        assertEquals(listOf(live), EffectiveBudgets.forMonth(listOf(live, cleared), october))
    }

    @Test
    fun `an empty budget list resolves to no budgets`() {
        assertEquals(emptyList<Budget>(), EffectiveBudgets.forMonth(emptyList(), october))
    }
}
