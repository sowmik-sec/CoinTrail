package com.cointrail.data.sync

import androidx.room.withTransaction
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.repo.toDomain
import com.cointrail.data.repo.toEntity

/**
 * A namespace's local state, read and written through Room (SPEC §7). [mergeRemote] reads the
 * current rows, merges the remote journal last-write-wins, and writes the result — all inside one
 * transaction, so a concurrent local edit is either included in the read or applied after the
 * write, never lost in between.
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

    private suspend fun readAll(): SyncJournal = SyncJournal(
        expenses = database.expenseDao().all().map { it.toDomain() },
        categories = database.categoryDao().all().map { it.toDomain() },
        paymentMethods = database.paymentMethodDao().all().map { it.toDomain() },
        budgets = database.budgetDao().all().map { it.toDomain() },
        recurring = database.recurringSeriesDao().all().map { it.toDomain() },
    )

    private suspend fun writeAll(journal: SyncJournal) {
        database.expenseDao().replaceAll(journal.expenses.map { it.toEntity() })
        database.categoryDao().replaceAll(journal.categories.map { it.toEntity() })
        database.paymentMethodDao().replaceAll(journal.paymentMethods.map { it.toEntity() })
        database.budgetDao().replaceAll(journal.budgets.map { it.toEntity() })
        database.recurringSeriesDao().replaceAll(journal.recurring.map { it.toEntity() })
    }
}
