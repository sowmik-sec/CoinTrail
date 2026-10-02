package com.cointrail.data.recurring

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the periodic WorkManager job that generates due recurring occurrences in the background
 * (SPEC §6.7). The job runs even when the app is closed; WorkManager persists it across reboots.
 */
interface RecurringGenerationScheduler {

    /** (Re)registers the periodic generation job. Safe to call repeatedly. */
    fun enqueuePeriodic()
}

class WorkManagerRecurringGenerationScheduler(context: Context) : RecurringGenerationScheduler {

    private val appContext = context.applicationContext

    override fun enqueuePeriodic() {
        val request = PeriodicWorkRequestBuilder<RecurringGenerationWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME: String = "cointrail_recurring_generation"
        const val INTERVAL_HOURS: Long = 6
    }
}
