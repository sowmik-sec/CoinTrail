package com.cointrail.domain.budget

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The single seam that decides which budgets govern a given month (SPEC §6.5): a month's override
 * wins over the scope's default, an explicit "no budget" month contributes nothing, and a removed
 * override hands the month back to the default. Home, the Monthly report and threshold planning all
 * read their limits through here.
 */
class EffectiveBudgetsTest {

    private val ts: LocalDateTime = LocalDateTime.of(2026, 10, 1, 0, 0)
    private val october: YearMonth = YearMonth.of(2026, 10)
    private val november: YearMonth = YearMonth.of(2026, 11)

    private fun default(id: String, categoryId: String?, paisa: Long) = Budget(
        id = id, categoryId = categoryId, monthlyLimit = Money(paisa), updatedAt = ts,
    )

    private fun override(id: String, categoryId: String?, month: YearMonth, paisa: Long?) = Budget(
        id = id, categoryId = categoryId, month = month, monthlyLimit = paisa?.let(::Money), updatedAt = ts,
    )

    private fun cleared(budget: Budget) = budget.copy(deletedAt = ts)

    @Test
    fun `defaults govern every month without an override`() {
        val overall = default("overall", null, 200_000)
        val food = default("food", "preset-food", 100_000)

        assertEquals(
            listOf(overall, food),
            EffectiveBudgets.forMonth(listOf(overall, food), october),
        )
        assertEquals(
            listOf(overall, food),
            EffectiveBudgets.forMonth(listOf(overall, food), november),
        )
    }

    @Test
    fun `a month's override wins over the default of its own scope`() {
        val overall = default("overall", null, 200_000)
        val shopping = default("shopping", "preset-shopping", 100_000)
        val eid = override("eid", "preset-shopping", october, 900_000)

        val resolved = EffectiveBudgets.forMonth(listOf(overall, shopping, eid), october)

        assertEquals(listOf("overall", "eid"), resolved.map { it.id })
        assertEquals(Money(900_000), resolved.last().monthlyLimit)
    }

    @Test
    fun `an override governs only its own month`() {
        val food = default("food", "preset-food", 100_000)
        val eid = override("eid", "preset-food", october, 900_000)

        val november = EffectiveBudgets.forMonth(listOf(food, eid), november)

        assertEquals(listOf("food"), november.map { it.id })
    }

    @Test
    fun `a no-budget month contributes nothing for its scope while others still govern`() {
        val overall = default("overall", null, 200_000)
        val food = default("food", "preset-food", 100_000)
        val quiet = override("quiet", "preset-food", october, null)

        assertEquals(
            listOf(overall),
            EffectiveBudgets.forMonth(listOf(overall, food, quiet), october),
        )
    }

    @Test
    fun `a removed override hands the month back to the default`() {
        val food = default("food", "preset-food", 100_000)
        val abandoned = cleared(override("eid", "preset-food", october, 900_000))

        assertEquals(
            listOf("food"),
            EffectiveBudgets.forMonth(listOf(food, abandoned), october).map { it.id },
        )
    }

    @Test
    fun `a cleared default leaves the month with no budget`() {
        val cleared = cleared(default("overall", null, 200_000))

        assertEquals(emptyList<Budget>(), EffectiveBudgets.forMonth(listOf(cleared), october))
    }

    @Test
    fun `an override can govern a month for a scope with no default`() {
        val trip = override("trip", "preset-transport", october, 300_000)

        assertEquals(
            listOf("trip"),
            EffectiveBudgets.forMonth(listOf(trip), october).map { it.id },
        )
    }

    @Test
    fun `tombstoned budgets never govern a month`() {
        val live = default("overall", null, 200_000)
        val clearedFood = cleared(default("food", "preset-food", 100_000))

        assertEquals(listOf(live), EffectiveBudgets.forMonth(listOf(live, clearedFood), october))
    }

    @Test
    fun `an empty budget list resolves to no budgets`() {
        assertEquals(emptyList<Budget>(), EffectiveBudgets.forMonth(emptyList(), october))
    }
}
