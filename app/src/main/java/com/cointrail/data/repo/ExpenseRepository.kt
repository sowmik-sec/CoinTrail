package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.db.ExpenseDao
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.DailyTotal
import com.cointrail.domain.model.Expense
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

class ExpenseRepository(
    private val dao: ExpenseDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ExpenseStore {

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        occurredAt: LocalDateTime,
    ): String {
        val timestamp = now()
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            categoryId = categoryId,
            note = note,
            paymentMethodId = paymentMethodId,
            occurredAt = occurredAt,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        dao.upsert(expense.toEntity())
        return expense.id
    }

    suspend fun update(expense: Expense) {
        dao.upsert(expense.copy(updatedAt = now()).toEntity())
    }

    suspend fun softDelete(id: String) {
        dao.softDelete(id, now())
    }

    override fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<Expense>> =
        dao.observeBetween(from, to).map { rows -> rows.map { it.toDomain() } }

    override fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Money> =
        dao.observeTotalBetween(from, to).map { Money(it) }

    fun observeDailyTotals(month: YearMonth): Flow<List<DailyTotal>> {
        val from = month.atDay(1).atStartOfDay()
        val to = month.plusMonths(1).atDay(1).atStartOfDay()
        return dao.observeDailyTotals(from, to)
            .map { rows -> rows.map { DailyTotal(LocalDate.parse(it.day), Money(it.totalPaisa)) } }
    }

    fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotal>> =
        dao.observeCategoryTotals(from, to)
            .map { rows -> rows.map { CategoryTotal(it.categoryId, Money(it.totalPaisa)) } }

    suspend fun changesSince(since: LocalDateTime): List<Expense> =
        dao.changesSince(since).map { it.toDomain() }
}
