package com.cointrail.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cointrail.CoinTrailApplication

/**
 * Syncs the signed-in account's journal while the app is closed (SPEC §7). Safe to run repeatedly:
 * the cycle merges idempotently, so a redundant run converges rather than duplicating anything.
 * When no account is signed in the manager is a no-op, so the job still succeeds.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CoinTrailApplication).container
        container.sync.syncNow()
        return Result.success()
    }
}
