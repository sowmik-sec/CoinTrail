package com.cointrail.domain.budget

import com.cointrail.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetAlertEvaluatorTest {

    private fun progress(
        id: String,
        spentPaisa: Long,
        limitPaisa: Long = 10_000,
        label: String = "Food",
    ) = BudgetProgress(
        budgetId = id,
        categoryId = "preset-food",
        label = label,
        limit = Money(limitPaisa),
        spent = Money(spentPaisa),
    )

    @Test
    fun `no alert before eighty percent`() {
        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b1", 7_999)), emptySet()).isEmpty())
    }

    @Test
    fun `crossing eighty percent fires one warning with the amounts`() {
        val alert = BudgetAlertEvaluator.plan(listOf(progress("b1", 8_000)), emptySet()).single()

        assertEquals("b1", alert.budgetId)
        assertEquals("Food", alert.label)
        assertEquals(BudgetAlertLevel.WARNING, alert.level)
        assertEquals(Money(10_000), alert.limit)
        assertEquals(Money(8_000), alert.spent)
        assertEquals(80, alert.percent)
    }

    @Test
    fun `a warning already fired this month does not fire again`() {
        val fired = setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING))

        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b1", 9_000)), fired).isEmpty())
    }

    @Test
    fun `crossing one hundred percent fires an exceeded alert after the warning`() {
        val fired = setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING))

        val alert = BudgetAlertEvaluator.plan(listOf(progress("b1", 10_000)), fired).single()

        assertEquals(BudgetAlertLevel.EXCEEDED, alert.level)
    }

    @Test
    fun `jumping straight past the limit fires only the exceeded alert`() {
        val alerts = BudgetAlertEvaluator.plan(listOf(progress("b1", 15_000)), emptySet())

        assertEquals(listOf(BudgetAlertLevel.EXCEEDED), alerts.map { it.level })
    }

    @Test
    fun `an exceeded alert already fired does not fire again`() {
        val fired = setOf(
            BudgetAlertKey("b1", BudgetAlertLevel.WARNING),
            BudgetAlertKey("b1", BudgetAlertLevel.EXCEEDED),
        )

        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b1", 20_000)), fired).isEmpty())
    }

    @Test
    fun `each budget is evaluated independently`() {
        val alerts = BudgetAlertEvaluator.plan(
            listOf(
                progress("food", 8_500, label = "Food"),
                progress("rent", 11_000, label = "Rent"),
                progress("transport", 1_000, label = "Transport"),
            ),
            emptySet(),
        )

        assertEquals(
            listOf("food" to BudgetAlertLevel.WARNING, "rent" to BudgetAlertLevel.EXCEEDED),
            alerts.map { it.budgetId to it.level },
        )
    }

    @Test
    fun `the alert carries the scope label for the notification`() {
        val alert = BudgetAlertEvaluator.plan(
            listOf(progress("overall", 500_000, limitPaisa = 500_000, label = "Overall")),
            emptySet(),
        ).single()

        assertEquals("Overall", alert.label)
    }

    @Test
    fun `reaching the threshold in a fresh month fires again`() {
        // A new month starts with an empty fired set, so the same progress alerts once more.
        assertEquals(1, BudgetAlertEvaluator.plan(listOf(progress("b1", 8_000)), emptySet()).size)
    }
}
