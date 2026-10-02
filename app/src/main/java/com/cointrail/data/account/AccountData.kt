package com.cointrail.data.account

import android.content.Context
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.recurring.RecurringExpenseGenerator
import com.cointrail.data.repo.BudgetRepository
import com.cointrail.data.repo.CategoryRepository
import com.cointrail.data.repo.ExpenseRepository
import com.cointrail.data.repo.PaymentMethodRepository
import com.cointrail.data.repo.RecurringSeriesRepository
import com.cointrail.data.sync.RoomSyncLocalStore
import com.cointrail.data.sync.SyncLocalStore

/**
 * The open database and repositories for a single account namespace. Everything user-facing is
 * reached through the [AccountManager]'s active scope, so switching accounts swaps this whole object
 * and two accounts on one device never share a database (SPEC §7).
 */
class AccountData(
    context: Context,
    val key: String,
) {

    private val database: CoinTrailDatabase = CoinTrailDatabase.create(context, key)

    val expenses: ExpenseRepository = ExpenseRepository(database.expenseDao())
    val categories: CategoryRepository = CategoryRepository(database.categoryDao())
    val paymentMethods: PaymentMethodRepository = PaymentMethodRepository(database.paymentMethodDao())
    val budgets: BudgetRepository = BudgetRepository(database.budgetDao())
    val recurring: RecurringSeriesRepository = RecurringSeriesRepository(database.recurringSeriesDao())

    /** Creates due recurring occurrences on app open and from the background job (SPEC §6.7). */
    val recurringGenerator: RecurringExpenseGenerator =
        RecurringExpenseGenerator(database.recurringSeriesDao(), database.expenseDao())

    /** This namespace's full-row state, read and written by Drive sync (SPEC §7). */
    val syncStore: SyncLocalStore = RoomSyncLocalStore(database)

    /** Seeds the preset categories and payment methods for this namespace exactly once. */
    suspend fun seedDefaults() {
        categories.ensureSeeded()
        paymentMethods.ensureSeeded()
    }

    /** Releases the underlying database; the file stays on disk unless the namespace is removed. */
    fun close() {
        database.close()
    }
}
