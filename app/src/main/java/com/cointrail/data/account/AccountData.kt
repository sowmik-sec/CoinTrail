package com.cointrail.data.account

import android.content.Context
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.recurring.RecurringExpenseGenerator
import com.cointrail.data.repo.BudgetRepository
import com.cointrail.data.repo.CatalogRepository
import com.cointrail.data.repo.ExpenseRepository
import com.cointrail.data.repo.RecurringSeriesRepository
import com.cointrail.data.sync.RoomSyncLocalStore
import com.cointrail.data.sync.SyncLocalStore

/**
 * The open database and repositories for a single account namespace. Entirely user-facing data lives
 * behind [AccountManager]'s account-scoped stores, so two accounts on one device never share a
 * database (SPEC §7).
 */
class AccountData(
    context: Context,
    val key: String,
    databaseFactory: (String) -> CoinTrailDatabase = { CoinTrailDatabase.create(context, it) },
) {

    private val database: CoinTrailDatabase = databaseFactory(key)

    val expenses: ExpenseRepository = ExpenseRepository(database.expenseDao())

    /** The namespace's one catalog store over its two catalog tables (ticket 25). */
    val catalogs: CatalogRepository = CatalogRepository(database.catalogDao())
    val budgets: BudgetRepository = BudgetRepository(database.budgetDao())
    val recurring: RecurringSeriesRepository = RecurringSeriesRepository(database.recurringSeriesDao())

    /** Creates due recurring occurrences on app open and from the background job (SPEC §6.7). */
    val recurringGenerator: RecurringExpenseGenerator =
        RecurringExpenseGenerator(database.recurringSeriesDao(), database.expenseDao())

    /** This namespace's full-row state, read and written by Drive sync (SPEC §7). */
    val syncStore: SyncLocalStore = RoomSyncLocalStore(database)

    /** Releases the underlying database; the file stays on disk unless the namespace is removed. */
    fun close() {
        database.close()
    }
}
