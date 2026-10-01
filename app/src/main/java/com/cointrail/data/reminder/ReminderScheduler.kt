package com.cointrail.data.reminder

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.cointrail.domain.reminder.ReminderPolicy
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Owns the WorkManager job that fires the daily reminder (SPEC §6.6). Enqueues a single unique
 * one-time job for the next occurrence of the configured time. Because WorkManager persists its
 * queue, the job survives device restarts and runs late (catch-up) if the phone was off when it was
 * due.
 */
interface ReminderScheduler {

    /** (Re)schedules the next reminder from the configured time. Safe to call repeatedly. */
    suspend fun scheduleNext()
}

class WorkManagerReminderScheduler(
    context: Context,
    private val settings: ReminderSettings,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ReminderScheduler {

    private val appContext = context.applicationContext

    override suspend fun scheduleNext() {
        val current = now()
        val trigger = ReminderPolicy.nextTrigger(settings.time(), current)
        val delayMillis = Duration.between(current, trigger).toMillis().coerceAtLeast(0L)

        val request = OneTimeWorkRequestBuilder<DailyReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME: String = "cointrail_daily_reminder"
    }
}
