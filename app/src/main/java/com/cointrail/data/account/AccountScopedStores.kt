package com.cointrail.data.account

import com.cointrail.core.Money
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.data.repo.RecurringStore
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Account-scoped views of the five user-facing stores (ADR-0003): each adapter implements the store
 * interface the screens already depend on and resolves the active account scope itself, so a caller
 * holds one store for the life of the app and an account change arrives as a flow emission rather
 * than a restart. Flow reads re-resolve per subscription; suspend calls resolve the scope when they
 * run. The store interfaces are unchanged, so no screen or view model constructor changes.
 */
class AccountScopedExpenseStore(private val manager: AccountManager) : ExpenseStore {

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        occurredAt: LocalDateTime,
    ): String = manager.current.expenses.add(amount, categoryId, note, paymentMethodId, occurredAt)

    override suspend fun update(expense: Expense) = manager.current.expenses.update(expense)

    override suspend fun delete(id: String) = manager.current.expenses.delete(id)

    override suspend fun restore(id: String) = manager.current.expenses.restore(id)

    override suspend fun findById(id: String): Expense? = manager.current.expenses.findById(id)

    override suspend fun loadBetween(from: LocalDateTime, to: LocalDateTime): List<Expense> =
        manager.current.expenses.loadBetween(from, to)

    override fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>> =
        manager.scoped { it.expenses.observeBetween(from, to) }

    override fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money> =
        manager.scoped { it.expenses.observeTotalBetween(from, to) }

    override fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>> =
        manager.scoped { it.expenses.observeCategoryTotals(from, to) }

    override fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>> =
        manager.scoped { it.expenses.observeDailyTotals(month) }
}

class AccountScopedCategoryStore(private val manager: AccountManager) : CategoryStore {

    override fun observeAll(): Flow<List<Category>> = manager.scoped { it.categories.observeAll() }

    override suspend fun add(name: String): String = manager.current.categories.add(name)

    override suspend fun rename(id: String, name: String) = manager.current.categories.rename(id, name)

    override suspend fun setHidden(id: String, hidden: Boolean) =
        manager.current.categories.setHidden(id, hidden)
}

class AccountScopedPaymentMethodStore(private val manager: AccountManager) : PaymentMethodStore {

    override fun observeAll(): Flow<List<PaymentMethod>> =
        manager.scoped { it.paymentMethods.observeAll() }

    override suspend fun add(name: String): String = manager.current.paymentMethods.add(name)

    override suspend fun rename(id: String, name: String) =
        manager.current.paymentMethods.rename(id, name)

    override suspend fun setHidden(id: String, hidden: Boolean) =
        manager.current.paymentMethods.setHidden(id, hidden)
}

class AccountScopedBudgetStore(private val manager: AccountManager) : BudgetStore {

    override fun observeAll(): Flow<List<Budget>> = manager.scoped { it.budgets.observeAll() }

    override suspend fun setDefault(categoryId: String?, monthlyLimit: Money): String =
        manager.current.budgets.setDefault(categoryId, monthlyLimit)

    override suspend fun setOverride(categoryId: String?, month: YearMonth, monthlyLimit: Money): String =
        manager.current.budgets.setOverride(categoryId, month, monthlyLimit)

    override suspend fun setNoBudget(categoryId: String?, month: YearMonth): String =
        manager.current.budgets.setNoBudget(categoryId, month)

    override suspend fun removeOverride(categoryId: String?, month: YearMonth) =
        manager.current.budgets.removeOverride(categoryId, month)

    override suspend fun clear(id: String) = manager.current.budgets.clear(id)
}

class AccountScopedRecurringStore(private val manager: AccountManager) : RecurringStore {

    override fun observeAll(): Flow<List<RecurringSeries>> = manager.scoped { it.recurring.observeAll() }

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
        startMonth: YearMonth,
    ): String = manager.current.recurring.add(amount, categoryId, note, paymentMethodId, dayOfMonth, startMonth)

    override suspend fun update(
        id: String,
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
    ) = manager.current.recurring.update(id, amount, categoryId, note, paymentMethodId, dayOfMonth)

    override suspend fun setPaused(id: String, paused: Boolean) =
        manager.current.recurring.setPaused(id, paused)

    override suspend fun delete(id: String) = manager.current.recurring.delete(id)
}

/** Re-subscribes to [flowForScope] against the open scope whenever the active scope changes. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> AccountManager.scoped(flowForScope: (AccountData) -> Flow<T>): Flow<T> =
    accountKey.flatMapLatest { key -> flowForScope(scope(key)) }
