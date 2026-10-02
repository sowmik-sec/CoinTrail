package com.cointrail.domain.recurring

import com.cointrail.domain.model.RecurringSeries
import java.time.LocalDate
import java.time.YearMonth

/**
 * Pure policy for automatic monthly recurring occurrences (SPEC §6.7).
 *
 * A series produces at most one ordinary expense per month, on its [RecurringSeries.dayOfMonth]
 * (a day of 31 clamps to the shorter month's end). Generation is driven by the current month only:
 * once a month's occurrence has been created, [RecurringSeries.lastGeneratedMonth] records it, so
 * the same month is never generated twice and editing or deleting the generated expense never makes
 * it reappear.
 */
object RecurringGeneration {

    /** The local date of the occurrence in [month]; [dayOfMonth] is clamped to the month's length. */
    fun occurrenceDate(dayOfMonth: Int, month: YearMonth): LocalDate =
        month.atDay(dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    /**
     * The month that is due an occurrence as of [today], or null when there is nothing to generate.
     * Only the current month is considered. A paused series, one that has not reached its
     * [RecurringSeries.startMonth], one already generated this month, and one whose occurrence day
     * has not arrived are all not due.
     */
    fun dueMonth(series: RecurringSeries, today: LocalDate): YearMonth? {
        if (series.isPaused) return null
        val month = YearMonth.from(today)
        if (month < series.startMonth) return null
        if (series.lastGeneratedMonth != null && series.lastGeneratedMonth >= month) return null
        return month.takeIf { occurrenceDate(series.dayOfMonth, it) <= today }
    }
}
