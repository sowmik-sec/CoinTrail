package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Ports the UI layer talks to. They expose only the operations a screen needs, so screens can be
 * tested against tiny in-memory fakes instead of a full Room database.
 */
interface ExpenseStore {

    suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        occurredAt: LocalDateTime,
    ): String

    suspend fun update(expense: Expense)

    /** Tombstones the expense: it disappears from every live view but survives for sync. */
    suspend fun delete(id: String)

    /** Undoes a [delete], bringing the expense back exactly as it was. */
    suspend fun restore(id: String)

    suspend fun findById(id: String): Expense?

    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>>

    fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money>

    /** A live per-category breakdown of live expenses in `[from, to)`, ordered by total desc. */
    fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>>

    /** A one-shot snapshot of live expenses in `[from, to)`, for the CSV export. */
    suspend fun loadBetween(from: LocalDateTime, to: LocalDateTime): List<Expense>
}

interface CategoryStore {

    fun observeAll(): Flow<List<Category>>

    suspend fun add(name: String): String

    suspend fun rename(id: String, name: String)

    /** Hides or shows the category; hidden ones disappear from pickers but keep their history. */
    suspend fun setHidden(id: String, hidden: Boolean)
}

interface PaymentMethodStore {

    fun observeAll(): Flow<List<PaymentMethod>>

    suspend fun add(name: String): String

    suspend fun rename(id: String, name: String)

    /** Hides or shows the payment method; hidden ones disappear from pickers but keep their history. */
    suspend fun setHidden(id: String, hidden: Boolean)
}

interface BudgetStore {

    fun observeAll(): Flow<List<Budget>>

    /** Sets (or replaces) the monthly limit for a scope; `categoryId` null is the overall budget. */
    suspend fun set(categoryId: String?, monthlyLimit: Money): String

    /** Clears a budget; the row is tombstoned so sync can propagate the removal. */
    suspend fun clear(id: String)
}
