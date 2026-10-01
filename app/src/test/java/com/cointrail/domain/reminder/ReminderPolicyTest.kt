package com.cointrail.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPolicyTest {

    private val reminderTime: LocalTime = LocalTime.of(21, 30)

    @Test
    fun `next trigger is later today when the time is still ahead`() {
        val now = LocalDateTime.of(2026, 10, 5, 9, 0)

        assertEquals(LocalDateTime.of(2026, 10, 5, 21, 30), ReminderPolicy.nextTrigger(reminderTime, now))
    }

    @Test
    fun `next trigger is tomorrow once the time has passed`() {
        val now = LocalDateTime.of(2026, 10, 5, 22, 0)

        assertEquals(LocalDateTime.of(2026, 10, 6, 21, 30), ReminderPolicy.nextTrigger(reminderTime, now))
    }

    @Test
    fun `next trigger is tomorrow when now is exactly the reminder time`() {
        val now = LocalDateTime.of(2026, 10, 5, 21, 30)

        assertEquals(LocalDateTime.of(2026, 10, 6, 21, 30), ReminderPolicy.nextTrigger(reminderTime, now))
    }

    @Test
    fun `next trigger rolls across a month boundary`() {
        val now = LocalDateTime.of(2026, 10, 31, 23, 0)

        assertEquals(LocalDateTime.of(2026, 11, 1, 21, 30), ReminderPolicy.nextTrigger(reminderTime, now))
    }

    @Test
    fun `only an entirely unlogged day is nudged`() {
        assertTrue(ReminderPolicy.shouldNotify(expensesLogged = 0))
        assertFalse(ReminderPolicy.shouldNotify(expensesLogged = 1))
        assertFalse(ReminderPolicy.shouldNotify(expensesLogged = 4))
    }

    @Test
    fun `day rollover is driven by the local calendar day`() {
        val nextDay = ReminderPolicy.nextTrigger(reminderTime, LocalDateTime.of(2026, 10, 5, 23, 59))
        assertEquals(LocalDate.of(2026, 10, 6), nextDay.toLocalDate())
    }
}
