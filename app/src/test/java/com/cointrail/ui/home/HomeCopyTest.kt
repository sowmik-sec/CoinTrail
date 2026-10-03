package com.cointrail.ui.home

import com.cointrail.core.Money
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCopyTest {

    @Test
    fun `a rise reads as more than last month`() {
        assertEquals("৳2,100 more than September (12%)", describeMonthChange(Money(210_000), 12, "September"))
    }

    @Test
    fun `a fall reads as less than last month without a minus sign`() {
        assertEquals("৳900 less than September (8%)", describeMonthChange(Money(-90_000), -8, "September"))
    }

    @Test
    fun `no movement reads as the same`() {
        assertEquals("Same as September", describeMonthChange(Money.ZERO, 0, "September"))
    }

    @Test
    fun `entry counts are singular for one`() {
        assertEquals("1 entry", entryCount(1))
        assertEquals("4 entries", entryCount(4))
    }
}
