package com.cointrail.data.sync.drive

import com.cointrail.data.sync.SyncRemoteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the sync journal as one JSON file in the user's own Google Drive app-data folder
 * (`appDataFolder`). Because the folder lives in each user's Drive, per-account isolation is free:
 * two Google accounts can never read each other's file (SPEC §7).
 *
 * The Drive REST work is deliberately thin and untested — it is the transport, exactly like the
 * Play-Services sign-in flow — while the merge policy that guards the data is unit-tested separately.
 */
class GoogleDriveSyncRemoteStore(
    private val tokens: DriveAccessTokens,
) : SyncRemoteStore {

    override suspend fun readJournal(): String? = withContext(Dispatchers.IO) {
        val files = DriveFiles(tokens.accessToken())
        files.findByName(FILE_NAME)?.let { files.read(it.id) }
    }

    override suspend fun writeJournal(content: String): Unit = withContext(Dispatchers.IO) {
        val files = DriveFiles(tokens.accessToken())
        val existing = files.findByName(FILE_NAME)
        if (existing == null) {
            files.create(FILE_NAME, content)
        } else {
            files.update(existing.id, content)
        }
    }

    companion object {
        /** The one file that holds the merged journal for a signed-in account. */
        const val FILE_NAME: String = "cointrail-sync-journal.json"
    }
}
