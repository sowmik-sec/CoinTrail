package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.YearMonth

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

    /** A live per-day total for the live expenses of [month], for the monthly heatmap. */
    fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>>

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

    /**
     * Sets (or replaces) a scope's **default budget** — the standing monthly limit that governs
     * every month without an override; `categoryId` null is the overall budget.
     */
    suspend fun setDefault(categoryId: String?, monthlyLimit: Money): String

    /** Sets (or replaces) a month's budget override limit for a scope; any month, past or future. */
    suspend fun setOverride(categoryId: String?, month: YearMonth, monthlyLimit: Money): String

    /** Marks a month as explicitly "no budget" for a scope — distinct from removing an override. */
    suspend fun setNoBudget(categoryId: String?, month: YearMonth): String

    /** Removes a month's override, handing that month back to the default; a no-op without one. */
    suspend fun removeOverride(categoryId: String?, month: YearMonth)

    /** Tombstones a budget row so sync can propagate the removal. */
    suspend fun clear(id: String)
}

interface RecurringStore {

    fun observeAll(): Flow<List<RecurringSeries>>

    /** Creates a series and returns its id. [startMonth] is the first month it may generate for. */
    suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
        startMonth: YearMonth,
    ): String

    /** Edits a series in place; its generated expenses and generation marker are untouched. */
    suspend fun update(
        id: String,
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
    )

    /** Pauses or resumes automatic generation for the series. */
    suspend fun setPaused(id: String, paused: Boolean)

    /** Tombstones the series; the expenses it already generated are kept (SPEC §6.7). */
    suspend fun delete(id: String)
}
