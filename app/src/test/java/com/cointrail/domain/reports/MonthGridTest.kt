package com.cointrail.domain.reports

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {

    @Test
    fun `a month that starts on the first day of the week has no leading blanks`() {
        // 2026-06-01 is a Monday.
        val cells = MonthGrid.cells(YearMonth.of(2026, 6))

        assertEquals(LocalDate.of(2026, 6, 1), cells.first())
    }

    @Test
    fun `leading blanks fill the week before the first day`() {
        // 2026-10-01 is a Thursday, so Monday-first leaves Mon/Tue/Wed blank.
        val cells = MonthGrid.cells(YearMonth.of(2026, 10))

        assertEquals(listOf(null, null, null), cells.take(3))
        assertEquals(LocalDate.of(2026, 10, 1), cells[3])
    }

    @Test
    fun `a different first day of week shifts the leading blanks`() {
        // Sunday-first leaves Sun/Mon/Tue/Wed blank before Thursday.
        val cells = MonthGrid.cells(YearMonth.of(2026, 10), firstDayOfWeek = DayOfWeek.SUNDAY)

        assertEquals(listOf(null, null, null, null), cells.take(4))
        assertEquals(LocalDate.of(2026, 10, 1), cells[4])
    }

    @Test
    fun `the grid is padded out to whole weeks`() {
        // October 2026: 3 leading blanks + 31 days = 34, padded to 35 (5 rows).
        val cells = MonthGrid.cells(YearMonth.of(2026, 10))

        assertEquals(35, cells.size)
        assertEquals(0, cells.size % 7)
    }

    @Test
    fun `the days run in order with no gaps`() {
        val cells = MonthGrid.cells(YearMonth.of(2026, 10))

        assertEquals((1..31).map { LocalDate.of(2026, 10, it) }, cells.filterNotNull())
    }
}
