package com.cointrail.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cointrail.CoinTrailApplication

/**
 * Writes the weekly Drive snapshot while the app is closed (SPEC §7). [BackupManager] reports
 * failures as a result rather than throwing, so the job always succeeds; when no account is signed
 * in it is a no-op, and the next weekly run simply tries again.
 */
class WeeklyBackupWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CoinTrailApplication).container
        container.backup.backupNow()
        return Result.success()
    }
}
