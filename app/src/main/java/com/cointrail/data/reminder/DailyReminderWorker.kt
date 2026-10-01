package com.cointrail.data.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cointrail.CoinTrailApplication
import com.cointrail.domain.reminder.ReminderPolicy
import java.time.LocalDate

/**
 * Fires the daily nudge at the configured time (and late, after a reboot). It skips the
 * notification when the day already has an expense logged, then queues tomorrow's job so the chain
 * keeps running without the app being opened (SPEC §6.6).
 */
class DailyReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CoinTrailApplication).container
        val today = LocalDate.now()
        val loggedToday = container.expenses.countBetween(
            today.atStartOfDay(),
            today.plusDays(1).atStartOfDay(),
        )
        if (ReminderPolicy.shouldNotify(loggedToday)) {
            container.reminderNotifier.notifyReminder()
        }
        container.reminderScheduler.scheduleNext()
        return Result.success()
    }
}
