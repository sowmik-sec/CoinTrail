package com.cointrail.domain.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

object MonthGrid {

    /**
     * The calendar cells for [month]: leading blanks up to the first day of the week, then each day
     * in order, padded with trailing blanks so every row is a full week. A null cell is an empty slot.
     */
    fun cells(month: YearMonth, firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
        val leadingBlanks = Math.floorMod(month.atDay(1).dayOfWeek.value - firstDayOfWeek.value, DAYS_PER_WEEK)
        val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val filledCells = leadingBlanks + days.size
        val totalCells = ((filledCells + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK) * DAYS_PER_WEEK
        return List(totalCells) { index ->
            val dayIndex = index - leadingBlanks
            if (dayIndex in days.indices) days[dayIndex] else null
        }
    }

    private const val DAYS_PER_WEEK = 7
}
