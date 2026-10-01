package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.domain.model.Category
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
