package com.cointrail.domain.reports

import com.cointrail.core.Money
import com.cointrail.domain.model.CategoryTotal
import org.junit.Assert.assertEquals
import org.junit.Test

class MonthComparisonTest {

    private fun total(categoryId: String, paisa: Long) = CategoryTotal(categoryId, Money(paisa))

    @Test
    fun `the total delta is this month minus last month`() {
        val comparison = MonthComparisonCalculator.compare(
            current = listOf(total("food", 40_000)),
            previous = listOf(total("food", 25_000)),
        )

        assertEquals(Money(40_000), comparison.currentTotal)
        assertEquals(Money(25_000), comparison.previousTotal)
        assertEquals(Money(15_000), comparison.delta)
    }

    @Test
    fun `per-category deltas cover every category that spent in either month`() {
        val comparison = MonthComparisonCalculator.compare(
            current = listOf(total("food", 40_000), total("transport", 12_000)),
            previous = listOf(total("food", 25_000), total("rent", 50_000)),
        )

        val byId = comparison.categories.associateBy { it.categoryId }
        assertEquals(setOf("food", "transport", "rent"), byId.keys)
        assertEquals(Money(40_000) to Money(25_000), byId.getValue("food").current to byId.getValue("food").previous)
        assertEquals(Money(15_000), byId.getValue("food").delta)
        assertEquals(Money(12_000), byId.getValue("transport").delta)
        assertEquals(Money(-50_000), byId.getValue("rent").delta)
    }

    @Test
    fun `a category new this month counts its whole spend as the increase`() {
        val comparison = MonthComparisonCalculator.compare(
            current = listOf(total("transport", 12_000)),
            previous = emptyList(),
        )

        val row = comparison.categories.single()
        assertEquals(Money.ZERO, row.previous)
        assertEquals(Money(12_000), row.delta)
    }

    @Test
    fun `a category that stopped this month counts its whole spend as the decrease`() {
        val comparison = MonthComparisonCalculator.compare(
            current = emptyList(),
            previous = listOf(total("rent", 50_000)),
        )

        val row = comparison.categories.single()
        assertEquals(Money.ZERO, row.current)
        assertEquals(Money(-50_000), row.delta)
    }

    @Test
    fun `categories are ordered by the size of the change biggest first`() {
        val comparison = MonthComparisonCalculator.compare(
            current = listOf(total("small", 11_000), total("big", 90_000)),
            previous = listOf(total("small", 10_000), total("big", 10_000), total("shrink", 100_000)),
        )

        assertEquals(listOf("shrink", "big", "small"), comparison.categories.map { it.categoryId })
    }

    @Test
    fun `a month with no spending on either side is an empty comparison`() {
        val comparison = MonthComparisonCalculator.compare(current = emptyList(), previous = emptyList())

        assertEquals(Money.ZERO, comparison.currentTotal)
        assertEquals(Money.ZERO, comparison.previousTotal)
        assertEquals(Money.ZERO, comparison.delta)
        assertEquals(emptyList<CategoryComparison>(), comparison.categories)
    }
}
