package com.cointrail.domain.reports

import com.cointrail.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MonthSummaryTest {

    @Test
    fun `the delta is this month minus the previous month`() {
        val summary = MonthSummary(currentTotal = Money(115_000), previousTotal = Money(100_000))

        assertEquals(Money(15_000), summary.delta)
        assertEquals(15, summary.percentChange)
    }

    @Test
    fun `a fall in spending is a negative delta and percent`() {
        val summary = MonthSummary(currentTotal = Money(85_000), previousTotal = Money(100_000))

        assertEquals(Money(-15_000), summary.delta)
        assertEquals(-15, summary.percentChange)
    }

    @Test
    fun `without previous-month data there is no comparison at all`() {
        val summary = MonthSummary(currentTotal = Money(50_000), previousTotal = Money.ZERO)

        assertNull(summary.delta)
        assertNull(summary.percentChange)
    }

    @Test
    fun `equal months are a zero delta`() {
        val summary = MonthSummary(currentTotal = Money(100_000), previousTotal = Money(100_000))

        assertEquals(Money.ZERO, summary.delta)
        assertEquals(0, summary.percentChange)
    }

    @Test
    fun `the percent rounds to the nearest whole number and keeps the sign`() {
        val roundedDown = MonthSummary(currentTotal = Money(87_500), previousTotal = Money(100_000))
        val roundedUp = MonthSummary(currentTotal = Money(112_500), previousTotal = Money(100_000))

        assertEquals(-13, roundedDown.percentChange)
        assertEquals(13, roundedUp.percentChange)
    }
}
