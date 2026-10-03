package com.cointrail.ui.home

import com.cointrail.core.Money

/**
 * The hero's month-over-month line in plain words: "৳2,100 more than September (12%)",
 * "৳900 less than September (8%)", or "Same as September" when nothing moved.
 */
fun describeMonthChange(delta: Money, percent: Int, previousMonthName: String): String {
    val magnitude = Money(kotlin.math.abs(delta.paisa)).format()
    val percentMagnitude = kotlin.math.abs(percent)
    return when {
        delta.paisa > 0 -> "$magnitude more than $previousMonthName ($percentMagnitude%)"
        delta.paisa < 0 -> "$magnitude less than $previousMonthName ($percentMagnitude%)"
        else -> "Same as $previousMonthName"
    }
}

/** "1 entry" / "3 entries". */
fun entryCount(count: Int): String = if (count == 1) "1 entry" else "$count entries"
