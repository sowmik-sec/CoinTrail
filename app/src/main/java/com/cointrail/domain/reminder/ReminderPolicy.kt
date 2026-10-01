package com.cointrail.domain.reminder

import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Pure policy for the daily reminder (SPEC §6.6).
 *
 * The reminder fires once a day at a user-configurable local time (default 21:30). It is suppressed
 * on a day where at least one expense has already been logged, so the user is only nudged on days
 * that are still entirely unlogged.
 */
object ReminderPolicy {

    /** The next instant the reminder is due: today at [time] if it is still ahead, else tomorrow. */
    fun nextTrigger(time: LocalTime, now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(time)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    /**
     * Whether the reminder should be posted for a day on which [expensesLogged] live expenses exist.
     * Any logged expense is enough to suppress the nudge for that day.
     */
    fun shouldNotify(expensesLogged: Int): Boolean = expensesLogged == 0
}
