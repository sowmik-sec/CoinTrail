package com.cointrail.testing

import com.cointrail.core.Money
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime

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
}

class FakeCategoryStore(initial: List<Category> = emptyList()) : CategoryStore {

    private val categories = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<Category>> = categories
}

class FakePaymentMethodStore(initial: List<PaymentMethod> = emptyList()) : PaymentMethodStore {

    private val paymentMethods = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<PaymentMethod>> = paymentMethods
}
