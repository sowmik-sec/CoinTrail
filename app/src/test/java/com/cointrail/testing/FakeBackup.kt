package com.cointrail.testing

import com.cointrail.data.backup.BackupController
import com.cointrail.data.backup.SnapshotRef
import com.cointrail.data.backup.SnapshotRemoteStore
import com.cointrail.data.backup.SnapshotResult
import java.time.LocalDateTime

/** An in-memory [SnapshotRemoteStore] that can fail on any operation. */
class FakeSnapshotRemoteStore : SnapshotRemoteStore {

    private data class Entry(val name: String, val content: String, val at: LocalDateTime)

    private val entries = linkedMapOf<String, Entry>()
    private var nextId = 0

    var listError: Throwable? = null
    var readError: Throwable? = null
    var writeError: Throwable? = null

    var writeCount: Int = 0
        private set

    fun seed(content: String, at: LocalDateTime, id: String = "snapshot-${nextId++}"): SnapshotRef {
        val name = SnapshotRef.nameFor(at)
        entries[id] = Entry(name, content, at)
        return SnapshotRef(id = id, name = name, createdAt = at)
    }

    fun contentOf(id: String): String? = entries[id]?.content

    override suspend fun list(): List<SnapshotRef> {
        listError?.let { throw it }
        return entries.map { (id, entry) -> SnapshotRef(id, entry.name, entry.at) }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun read(id: String): String {
        readError?.let { throw it }
        return entries[id]?.content ?: throw IllegalArgumentException("No snapshot $id")
    }

    override suspend fun write(content: String, at: LocalDateTime): SnapshotRef {
        writeError?.let { throw it }
        writeCount++
        return seed(content, at)
    }
}

/** A scripted [BackupController] for the backup-viewmodel tests. */
class FakeBackupController : BackupController {

    private val arbitrary = SnapshotRef(
        id = "snapshot-1",
        name = SnapshotRef.nameFor(LocalDateTime.of(2026, 10, 2, 9, 0)),
        createdAt = LocalDateTime.of(2026, 10, 2, 9, 0),
    )

    var exportResult: SnapshotResult<String> = SnapshotResult.Success("{}")
    var importResult: SnapshotResult<Unit> = SnapshotResult.Success(Unit)
    var listResult: SnapshotResult<List<SnapshotRef>> = SnapshotResult.Success(emptyList())
    var backupResult: SnapshotResult<SnapshotRef> = SnapshotResult.Success(arbitrary)
    var restoreResult: SnapshotResult<Unit> = SnapshotResult.Success(Unit)

    var exportCount: Int = 0
        private set
    var importCount: Int = 0
        private set
    var listCount: Int = 0
        private set
    var backupCount: Int = 0
        private set
    var restoreCount: Int = 0
        private set

    var lastImported: String? = null
        private set
    var lastRestoredId: String? = null
        private set

    override suspend fun exportJson(): SnapshotResult<String> {
        exportCount++
        return exportResult
    }

    override suspend fun importJson(text: String): SnapshotResult<Unit> {
        importCount++
        lastImported = text
        return importResult
    }

    override suspend fun listSnapshots(): SnapshotResult<List<SnapshotRef>> {
        listCount++
        return listResult
    }

    override suspend fun backupNow(): SnapshotResult<SnapshotRef> {
        backupCount++
        return backupResult
    }

    override suspend fun restoreSnapshot(id: String): SnapshotResult<Unit> {
        restoreCount++
        lastRestoredId = id
        return restoreResult
    }
}
