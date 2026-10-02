package com.cointrail.data.recurring

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cointrail.CoinTrailApplication

/**
 * Creates any due recurring occurrences while the app is not open (SPEC §6.7), so rent and
 * subscriptions show up even if the app was not opened on their day. Safe to run repeatedly: the
 * generator never duplicates a month's entry.
 */
class RecurringGenerationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CoinTrailApplication).container
        container.recurringGenerator.generateDue()
        return Result.success()
    }
}
