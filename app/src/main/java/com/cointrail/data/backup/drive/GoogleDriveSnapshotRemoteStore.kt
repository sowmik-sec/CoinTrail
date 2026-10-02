package com.cointrail.data.backup.drive

import com.cointrail.data.backup.SnapshotRef
import com.cointrail.data.backup.SnapshotRemoteStore
import com.cointrail.data.sync.drive.DriveAccessTokens
import com.cointrail.data.sync.drive.DriveFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/**
 * Stores timestamped full backups as JSON files in the user's own Drive app-data folder, alongside
 * the sync journal (SPEC §7). Because the folder lives in each user's Drive, snapshots are isolated
 * per account for free; the weekly job and "Backup now" both write here.
 *
 * As with the sync transport, the Drive REST work is deliberately thin and untested while everything
 * that touches data (the backup format, the restore merge) is unit-tested.
 */
class GoogleDriveSnapshotRemoteStore(
    private val tokens: DriveAccessTokens,
) : SnapshotRemoteStore {

    override suspend fun list(): List<SnapshotRef> = withContext(Dispatchers.IO) {
        DriveFiles(tokens.accessToken())
            .listByPrefix(SnapshotRef.PREFIX)
            .mapNotNull { file -> SnapshotRef.fromDrive(file.id, file.name) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun read(id: String): String = withContext(Dispatchers.IO) {
        DriveFiles(tokens.accessToken()).read(id)
    }

    override suspend fun write(content: String, at: LocalDateTime): SnapshotRef = withContext(Dispatchers.IO) {
        val name = SnapshotRef.nameFor(at)
        val file = DriveFiles(tokens.accessToken()).create(name, content)
        SnapshotRef(id = file.id, name = file.name, createdAt = at)
    }
}
