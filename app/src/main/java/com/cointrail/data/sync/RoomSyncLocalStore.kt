package com.cointrail.data.sync

import androidx.room.withTransaction
import com.cointrail.data.db.CoinTrailDatabase

/**
 * A namespace's local state, read and written through Room (SPEC §7). [mergeRemote] reads the
 * current rows, merges the remote journal last-write-wins, and writes the result — all inside one
 * transaction, so a concurrent local edit is either included in the read or applied after the
 * write, never lost in between. Every table is read and written through its [JournalTable]
 * descriptor ([JournalTables.all]), so this file names none of them.
 */
class RoomSyncLocalStore(
    private val database: CoinTrailDatabase,
) : SyncLocalStore {

    override suspend fun read(): SyncJournal = database.withTransaction { readAll() }

    override suspend fun mergeRemote(remote: SyncJournal): SyncJournal = database.withTransaction {
        val merged = SyncJournalMerge.merge(readAll(), remote)
        writeAll(merged)
        merged
    }

    private suspend fun readAll(): SyncJournal = JournalTables.readAll(database)

    private suspend fun writeAll(journal: SyncJournal) = JournalTables.writeAll(database, journal)
}
