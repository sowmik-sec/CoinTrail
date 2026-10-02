package com.cointrail.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the periodic WorkManager job that writes a weekly snapshot to Drive (SPEC §7). The job is
 * constrained to a live network so an offline device simply waits, and WorkManager persists it
 * across reboots. A signed-out device is a no-op, so the job still succeeds.
 */
interface BackupScheduler {

    /** (Re)registers the weekly backup job. Safe to call repeatedly. */
    fun enqueuePeriodic()
}

class WorkManagerBackupScheduler(context: Context) : BackupScheduler {

    private val appContext = context.applicationContext

    override fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WeeklyBackupWorker>(INTERVAL_DAYS, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME: String = "cointrail_weekly_backup"
        const val INTERVAL_DAYS: Long = 7
    }
}
