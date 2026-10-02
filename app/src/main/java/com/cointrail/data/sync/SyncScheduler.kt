package com.cointrail.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Owns the periodic WorkManager job that syncs in the background (SPEC §7). The job is constrained
 * to a live network so an offline device simply waits; WorkManager persists it across reboots.
 */
interface SyncScheduler {

    /** (Re)registers the periodic sync job. Safe to call repeatedly. */
    fun enqueuePeriodic()
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {

    private val appContext = context.applicationContext

    override fun enqueuePeriodic() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val WORK_NAME: String = "cointrail_drive_sync"
        const val INTERVAL_HOURS: Long = 6
    }
}
