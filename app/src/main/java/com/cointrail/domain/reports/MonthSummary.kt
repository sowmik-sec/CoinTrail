package com.cointrail.domain.reports

import com.cointrail.core.Money

/**
 * A month's spending measured against the month before it (SPEC §6.11). There is nothing to
 * compare against until the previous month has data, so [delta] and [percentChange] stay null
 * until then.
 */
data class MonthSummary(
    val currentTotal: Money,
    val previousTotal: Money,
) {
    /** Positive when spending rose, negative when it fell; null without previous-month data. */
    val delta: Money? = if (previousTotal > Money.ZERO) currentTotal - previousTotal else null

    /** The percentage move vs the previous month, rounded away from zero; null without data. */
    val percentChange: Int? = delta?.let { change ->
        val magnitude = if (change.paisa < 0) -change.paisa else change.paisa
        val rounded = ((magnitude * 100 + previousTotal.paisa / 2) / previousTotal.paisa).toInt()
        if (change.paisa < 0) -rounded else rounded
    }
}
