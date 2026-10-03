package com.cointrail.testing

import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertStore
import com.cointrail.data.alerts.BudgetNotifier
import com.cointrail.data.reminder.ReminderScheduler
import com.cointrail.data.reminder.ReminderSettings
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.data.repo.RecurringStore
import com.cointrail.domain.budget.BudgetAlert
import com.cointrail.domain.budget.BudgetAlertKey
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

data class AddCall(
    val amount: Money,
    val categoryId: String,
    val note: String?,
    val paymentMethodId: String?,
    val occurredAt: LocalDateTime,
)

class FakeExpenseStore : ExpenseStore {

    val calls: MutableList<AddCall> = mutableListOf()
    val updates: MutableList<Expense> = mutableListOf()
    val deletes: MutableList<String> = mutableListOf()
    val restores: MutableList<String> = mutableListOf()

    private val all = MutableStateFlow<List<Expense>>(emptyList())

    fun seed(vararg expenses: Expense) {
        all.value = all.value + expenses
    }

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        occurredAt: LocalDateTime,
    ): String {
        calls += AddCall(amount, categoryId, note, paymentMethodId, occurredAt)
        val id = "expense-${calls.size}"
        seed(
            Expense(
                id = id,
                amount = amount,
                categoryId = categoryId,
                note = note,
                paymentMethodId = paymentMethodId,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            )
        )
        return id
    }

    override suspend fun update(expense: Expense) {
        updates += expense
        all.value = all.value.map { if (it.id == expense.id) expense else it }
    }

    override suspend fun delete(id: String) {
        deletes += id
        all.value = all.value.map { if (it.id == id) it.copy(deletedAt = it.updatedAt) else it }
    }

    override suspend fun restore(id: String) {
        restores += id
        all.value = all.value.map { if (it.id == id) it.copy(deletedAt = null) else it }
    }

    override suspend fun findById(id: String): Expense? = all.value.firstOrNull { it.id == id }

    override fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>> =
        all.map { expenses ->
            expenses.filter { !it.isDeleted && it.occurredAt >= from && it.occurredAt < to }
                .sortedByDescending { it.occurredAt }
        }

    override fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money> =
        all.map { expenses ->
            expenses.filter { !it.isDeleted && it.occurredAt >= from && it.occurredAt < to }
                .fold(Money.ZERO) { acc, expense -> acc + expense.amount }
        }

    override fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>> =
        all.map { expenses ->
            expenses.filter { !it.isDeleted && it.occurredAt >= from && it.occurredAt < to }
                .groupBy { it.categoryId }
                .map { (categoryId, list) ->
                    CategoryTotal(categoryId, list.fold(Money.ZERO) { acc, expense -> acc + expense.amount })
                }
                .sortedByDescending { it.total.paisa }
        }

    override fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>> =
        all.map { expenses ->
            val from = month.atDay(1).atStartOfDay()
            val to = month.plusMonths(1).atDay(1).atStartOfDay()
            expenses.filter { !it.isDeleted && it.occurredAt >= from && it.occurredAt < to }
                .groupBy { it.occurredAt.toLocalDate() }
                .map { (day, list) ->
                    DailyTotal(day, list.fold(Money.ZERO) { acc, expense -> acc + expense.amount })
                }
                .sortedBy { it.day }
        }

    override suspend fun loadBetween(from: LocalDateTime, to: LocalDateTime): List<Expense> =
        all.value.filter { !it.isDeleted && it.occurredAt >= from && it.occurredAt < to }
            .sortedByDescending { it.occurredAt }
}

class FakeCategoryStore(initial: List<Category> = emptyList()) : CategoryStore {

    private val categories = MutableStateFlow(initial)

    val addedNames: MutableList<String> = mutableListOf()
    val renames: MutableList<Pair<String, String>> = mutableListOf()
    val hiddenChanges: MutableList<Pair<String, Boolean>> = mutableListOf()

    override fun observeAll(): Flow<List<Category>> = categories

    override suspend fun add(name: String): String {
        addedNames += name
        val id = UUID.randomUUID().toString()
        categories.value = categories.value + Category(id = id, name = name, updatedAt = LocalDateTime.now())
        return id
    }

    override suspend fun rename(id: String, name: String) {
        renames += id to name
        categories.value = categories.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        hiddenChanges += id to hidden
        categories.value = categories.value.map { if (it.id == id) it.copy(isHidden = hidden) else it }
    }
}

class FakePaymentMethodStore(initial: List<PaymentMethod> = emptyList()) : PaymentMethodStore {

    private val paymentMethods = MutableStateFlow(initial)

    val addedNames: MutableList<String> = mutableListOf()
    val renames: MutableList<Pair<String, String>> = mutableListOf()
    val hiddenChanges: MutableList<Pair<String, Boolean>> = mutableListOf()

    override fun observeAll(): Flow<List<PaymentMethod>> = paymentMethods

    override suspend fun add(name: String): String {
        addedNames += name
        val id = UUID.randomUUID().toString()
        paymentMethods.value = paymentMethods.value + PaymentMethod(id = id, name = name, updatedAt = LocalDateTime.now())
        return id
    }

    override suspend fun rename(id: String, name: String) {
        renames += id to name
        paymentMethods.value = paymentMethods.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        hiddenChanges += id to hidden
        paymentMethods.value = paymentMethods.value.map { if (it.id == id) it.copy(isHidden = hidden) else it }
    }
}

class FakeBudgetStore : BudgetStore {

    private val budgets = MutableStateFlow<List<Budget>>(emptyList())

    val defaultSets: MutableList<Pair<String?, Money>> = mutableListOf()
    val overrideSets: MutableList<Triple<String?, YearMonth, Money>> = mutableListOf()
    val noBudgetSets: MutableList<Pair<String?, YearMonth>> = mutableListOf()
    val overrideRemovals: MutableList<Pair<String?, YearMonth>> = mutableListOf()
    val clears: MutableList<String> = mutableListOf()

    fun seed(vararg items: Budget) {
        budgets.value = budgets.value + items
    }

    override fun observeAll(): Flow<List<Budget>> = budgets.map { rows -> rows.filterNot { it.isDeleted } }

    override suspend fun setDefault(categoryId: String?, monthlyLimit: Money): String =
        write(categoryId, month = null, monthlyLimit = monthlyLimit) { id ->
            defaultSets += categoryId to monthlyLimit
            id
        }

    override suspend fun setOverride(categoryId: String?, month: YearMonth, monthlyLimit: Money): String =
        write(categoryId, month, monthlyLimit) { id ->
            overrideSets += Triple(categoryId, month, monthlyLimit)
            id
        }

    override suspend fun setNoBudget(categoryId: String?, month: YearMonth): String =
        write(categoryId, month, monthlyLimit = null) { id ->
            noBudgetSets += categoryId to month
            id
        }

    override suspend fun removeOverride(categoryId: String?, month: YearMonth) {
        val scopeKey = categoryId ?: Budget.OVERALL_SCOPE
        val existing = budgets.value.firstOrNull {
            it.scopeKey == scopeKey && it.month == month
        } ?: return
        if (existing.isDeleted) return
        overrideRemovals += categoryId to month
        budgets.value = budgets.value.map {
            if (it.id == existing.id) it.copy(deletedAt = it.updatedAt) else it
        }
    }

    override suspend fun clear(id: String) {
        clears += id
        budgets.value = budgets.value.map {
            if (it.id == id) it.copy(deletedAt = it.updatedAt) else it
        }
    }

    /** Mirrors the repository: reuses the (scope, month) row so at most one row exists per key. */
    private suspend fun write(
        categoryId: String?,
        month: YearMonth?,
        monthlyLimit: Money?,
        record: (String) -> Unit,
    ): String {
        val scopeKey = categoryId ?: Budget.OVERALL_SCOPE
        val existing = budgets.value.firstOrNull { it.scopeKey == scopeKey && it.month == month }
        val id = existing?.id ?: "budget-${budgets.value.size + 1}"
        budgets.value = budgets.value.filterNot { it.id == id } + Budget(
            id = id,
            categoryId = categoryId,
            month = month,
            monthlyLimit = monthlyLimit,
            updatedAt = LocalDateTime.now(),
        )
        record(id)
        return id
    }
}

class FakeBudgetAlertStore : BudgetAlertStore {

    private val fired = mutableMapOf<YearMonth, MutableSet<BudgetAlertKey>>()

    override suspend fun firedFor(month: YearMonth): Set<BudgetAlertKey> = fired[month]?.toSet() ?: emptySet()

    override suspend fun markFired(month: YearMonth, keys: Set<BudgetAlertKey>) {
        fired.getOrPut(month) { mutableSetOf() } += keys
    }
}

class FakeBudgetNotifier(private val delivers: Boolean = true) : BudgetNotifier {

    val alerts: MutableList<BudgetAlert> = mutableListOf()

    override fun notify(alert: BudgetAlert): Boolean {
        alerts += alert
        return delivers
    }
}

class FakeReminderSettings(initial: LocalTime = ReminderSettings.DEFAULT_TIME) : ReminderSettings {

    private val state = MutableStateFlow(initial)

    val savedTimes: MutableList<LocalTime> = mutableListOf()

    var notificationPermissionRequested: Boolean = false
        private set

    override fun observeTime(): Flow<LocalTime> = state

    override suspend fun time(): LocalTime = state.value

    override suspend fun setTime(time: LocalTime) {
        savedTimes += time
        state.value = time
    }

    override suspend fun hasRequestedNotificationPermission(): Boolean = notificationPermissionRequested

    override suspend fun markNotificationPermissionRequested() {
        notificationPermissionRequested = true
    }
}

class FakeReminderScheduler : ReminderScheduler {

    var scheduleCount: Int = 0
        private set

    override suspend fun scheduleNext() {
        scheduleCount++
    }
}

class FakeRecurringStore : RecurringStore {

    data class CreateCall(
        val amount: Money,
        val categoryId: String,
        val note: String?,
        val paymentMethodId: String?,
        val dayOfMonth: Int,
        val startMonth: YearMonth,
    )

    data class UpdateCall(
        val id: String,
        val amount: Money,
        val categoryId: String,
        val note: String?,
        val paymentMethodId: String?,
        val dayOfMonth: Int,
    )

    private val series = MutableStateFlow<List<RecurringSeries>>(emptyList())

    val creates: MutableList<CreateCall> = mutableListOf()
    val updates: MutableList<UpdateCall> = mutableListOf()
    val pauseChanges: MutableList<Pair<String, Boolean>> = mutableListOf()
    val deletes: MutableList<String> = mutableListOf()

    fun seed(vararg items: RecurringSeries) {
        series.value = series.value + items
    }

    override fun observeAll(): Flow<List<RecurringSeries>> = series

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
        startMonth: YearMonth,
    ): String {
        creates += CreateCall(amount, categoryId, note, paymentMethodId, dayOfMonth, startMonth)
        val id = "series-${creates.size}"
        series.value = series.value + RecurringSeries(
            id = id,
            amount = amount,
            categoryId = categoryId,
            note = note,
            paymentMethodId = paymentMethodId,
            dayOfMonth = dayOfMonth,
            startMonth = startMonth,
            updatedAt = LocalDateTime.now(),
        )
        return id
    }

    override suspend fun update(
        id: String,
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
    ) {
        updates += UpdateCall(id, amount, categoryId, note, paymentMethodId, dayOfMonth)
        series.value = series.value.map { existing ->
            if (existing.id == id) {
                existing.copy(
                    amount = amount,
                    categoryId = categoryId,
                    note = note,
                    paymentMethodId = paymentMethodId,
                    dayOfMonth = dayOfMonth,
                )
            } else {
                existing
            }
        }
    }

    override suspend fun setPaused(id: String, paused: Boolean) {
        pauseChanges += id to paused
        series.value = series.value.map { if (it.id == id) it.copy(isPaused = paused) else it }
    }

    override suspend fun delete(id: String) {
        deletes += id
        series.value = series.value.filterNot { it.id == id }
    }
}
