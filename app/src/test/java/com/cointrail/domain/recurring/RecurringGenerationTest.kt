package com.cointrail.domain.recurring

import com.cointrail.core.Money
import com.cointrail.domain.model.RecurringSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RecurringGenerationTest {

    private val updatedAt: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)

    private fun series(
        dayOfMonth: Int = 15,
        startMonth: YearMonth = YearMonth.of(2026, 10),
        lastGeneratedMonth: YearMonth? = null,
        isPaused: Boolean = false,
    ) = RecurringSeries(
        id = "r1",
        amount = Money(150_000),
        categoryId = "preset-rent",
        note = null,
        paymentMethodId = "pm-bkash",
        dayOfMonth = dayOfMonth,
        startMonth = startMonth,
        lastGeneratedMonth = lastGeneratedMonth,
        isPaused = isPaused,
        updatedAt = updatedAt,
    )

    @Test
    fun `occurrence date is the day of month`() {
        assertEquals(LocalDate.of(2026, 10, 15), RecurringGeneration.occurrenceDate(15, YearMonth.of(2026, 10)))
    }

    @Test
    fun `day 31 clamps to the last day of a short month`() {
        assertEquals(LocalDate.of(2026, 4, 30), RecurringGeneration.occurrenceDate(31, YearMonth.of(2026, 4)))
        assertEquals(LocalDate.of(2026, 2, 28), RecurringGeneration.occurrenceDate(31, YearMonth.of(2026, 2)))
        assertEquals(LocalDate.of(2028, 2, 29), RecurringGeneration.occurrenceDate(31, YearMonth.of(2028, 2)))
        assertEquals(LocalDate.of(2026, 1, 31), RecurringGeneration.occurrenceDate(31, YearMonth.of(2026, 1)))
    }

    @Test
    fun `due on the day the occurrence arrives`() {
        val s = series(dayOfMonth = 15)

        assertEquals(YearMonth.of(2026, 10), RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `not due before the occurrence day`() {
        val s = series(dayOfMonth = 15)

        assertNull(RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 14)))
    }

    @Test
    fun `day 31 is due once the clamped day has arrived`() {
        val s = series(dayOfMonth = 31, startMonth = YearMonth.of(2026, 2))

        assertNull(RecurringGeneration.dueMonth(s, LocalDate.of(2026, 2, 27)))
        assertEquals(YearMonth.of(2026, 2), RecurringGeneration.dueMonth(s, LocalDate.of(2026, 2, 28)))
    }

    @Test
    fun `not due before the series start month`() {
        val s = series(startMonth = YearMonth.of(2026, 11))

        assertNull(RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `not due once the month has already been generated`() {
        val s = series(lastGeneratedMonth = YearMonth.of(2026, 10))

        assertNull(RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `a paused series is never due`() {
        val s = series(isPaused = true)

        assertNull(RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 20)))
    }

    @Test
    fun `a series started in a past month still generates only the current month`() {
        val s = series(startMonth = YearMonth.of(2026, 5))

        assertEquals(YearMonth.of(2026, 10), RecurringGeneration.dueMonth(s, LocalDate.of(2026, 10, 20)))
    }
}
