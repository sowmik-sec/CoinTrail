package com.cointrail.domain.budget

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
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
        assertEquals("preset-food", alert.scopeKey)
        assertEquals("Food", alert.label)
        assertEquals(BudgetAlertLevel.WARNING, alert.level)
        assertEquals(Money(10_000), alert.limit)
        assertEquals(Money(8_000), alert.spent)
        assertEquals(80, alert.percent)
    }

    @Test
    fun `a warning already fired this month does not fire again`() {
        // Fired keys are scoped: the progress row's category, not its row id.
        val fired = setOf(BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING))

        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b1", 9_000)), fired).isEmpty())
    }

    @Test
    fun `crossing one hundred percent fires an exceeded alert after the warning`() {
        val fired = setOf(BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING))

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
            BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING),
            BudgetAlertKey("preset-food", BudgetAlertLevel.EXCEEDED),
        )

        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b1", 20_000)), fired).isEmpty())
    }

    @Test
    fun `a mid-month budget change never re-arms a crossed threshold`() {
        // The month's budget was restated into a new row (new id, same scope) after the warning
        // fired; the new row must not raise the warning again.
        val fired = setOf(BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING))

        assertTrue(BudgetAlertEvaluator.plan(listOf(progress("b2-restated", 9_000)), fired).isEmpty())
    }

    @Test
    fun `a different scope is never silenced by another scope's fired threshold`() {
        val other = progress("b1", 9_000).copy(categoryId = "preset-rent")
        val fired = setOf(BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING))

        assertEquals(1, BudgetAlertEvaluator.plan(listOf(other), fired).size)
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

    @Test
    fun `the overall scope keys its alerts on the overall scope`() {
        val overall = progress("overall", 500_000, limitPaisa = 500_000, label = "Overall")
            .copy(categoryId = null)

        val alert = BudgetAlertEvaluator.plan(listOf(overall), emptySet()).single()

        assertEquals(Budget.OVERALL_SCOPE, alert.scopeKey)
    }
}
