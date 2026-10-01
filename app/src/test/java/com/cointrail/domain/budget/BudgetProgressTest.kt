package com.cointrail.domain.budget

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class BudgetProgressTest {

    private val ts: LocalDateTime = LocalDateTime.of(2026, 10, 1, 0, 0)

    private fun progress(spentPaisa: Long, limitPaisa: Long = 10_000) = BudgetProgress(
        budgetId = "b1",
        categoryId = "preset-food",
        label = "Food",
        limit = Money(limitPaisa),
        spent = Money(spentPaisa),
    )

    private fun budget(id: String, categoryId: String?, paisa: Long) =
        Budget(id = id, categoryId = categoryId, monthlyLimit = Money(paisa), updatedAt = ts)

    @Test
    fun `spending below eighty percent is on track`() {
        assertEquals(BudgetStatus.ON_TRACK, progress(spentPaisa = 0).status)
        assertEquals(BudgetStatus.ON_TRACK, progress(spentPaisa = 7_999).status)
    }

    @Test
    fun `reaching exactly eighty percent is a warning`() {
        assertEquals(BudgetStatus.WARNING, progress(spentPaisa = 8_000).status)
    }

    @Test
    fun `between eighty and one hundred percent stays a warning`() {
        assertEquals(BudgetStatus.WARNING, progress(spentPaisa = 9_999).status)
    }

    @Test
    fun `reaching exactly one hundred percent is exceeded`() {
        assertEquals(BudgetStatus.EXCEEDED, progress(spentPaisa = 10_000).status)
    }

    @Test
    fun `overspending is exceeded`() {
        assertEquals(BudgetStatus.EXCEEDED, progress(spentPaisa = 12_345).status)
    }

    @Test
    fun `percent is rounded to the nearest whole number`() {
        assertEquals(0, progress(spentPaisa = 0).percent)
        assertEquals(80, progress(spentPaisa = 8_000).percent)
        assertEquals(84, progress(spentPaisa = 8_350).percent)
        assertEquals(100, progress(spentPaisa = 10_000).percent)
        assertEquals(125, progress(spentPaisa = 12_500).percent)
    }

    @Test
    fun `percent rounds half up`() {
        // 83.5% of ৳100 is halfway between 83 and 84.
        assertEquals(84, progress(spentPaisa = 8_350, limitPaisa = 10_000).percent)
        assertEquals(83, progress(spentPaisa = 8_250, limitPaisa = 10_000).percent)
    }

    @Test
    fun `calculator pairs the overall budget with overall spending`() {
        val overall = budget("overall", null, 500_000)

        val result = BudgetProgressCalculator.calculate(
            budgets = listOf(overall),
            overallSpent = Money(120_000),
            spentByCategory = emptyMap(),
        )

        val progress = result.single()
        assertEquals("overall", progress.budgetId)
        assertEquals(null, progress.categoryId)
        assertEquals(Money(500_000), progress.limit)
        assertEquals(Money(120_000), progress.spent)
        assertEquals("Overall", progress.label)
    }

    @Test
    fun `calculator pairs a category budget with that category's spending`() {
        val food = budget("food-budget", "preset-food", 100_000)

        val result = BudgetProgressCalculator.calculate(
            budgets = listOf(food),
            overallSpent = Money(999_000),
            spentByCategory = mapOf("preset-food" to Money(45_000)),
            labelFor = { id -> if (id == "preset-food") "Food" else "Overall" },
        )

        val progress = result.single()
        assertEquals("Food", progress.label)
        assertEquals(Money(45_000), progress.spent)
    }

    @Test
    fun `a category with no spending yet shows zero`() {
        val food = budget("food-budget", "preset-food", 100_000)

        val result = BudgetProgressCalculator.calculate(
            budgets = listOf(food),
            overallSpent = Money(999_000),
            spentByCategory = emptyMap(),
        )

        assertEquals(Money.ZERO, result.single().spent)
        assertEquals(BudgetStatus.ON_TRACK, result.single().status)
    }

    @Test
    fun `calculator preserves budget order`() {
        val overall = budget("overall", null, 500_000)
        val food = budget("food-budget", "preset-food", 100_000)

        val result = BudgetProgressCalculator.calculate(
            budgets = listOf(overall, food),
            overallSpent = Money(0),
            spentByCategory = emptyMap(),
        )

        assertTrue(result.map { it.budgetId } == listOf("overall", "food-budget"))
    }
}
