package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.SeedData
import com.cointrail.data.db.BudgetDao
import com.cointrail.data.db.BudgetEntity
import com.cointrail.data.db.CategoryDao
import com.cointrail.data.db.PaymentMethodDao
import com.cointrail.data.db.RecurringSeriesDao
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID

class CategoryRepository(
    private val dao: CategoryDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : CategoryStore {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(SeedData.categories.map { it.copy(updatedAt = now()).toEntity() })
        }
    }

    override suspend fun add(name: String): String {
        val category = Category(name = name, sortOrder = dao.count(), updatedAt = now())
        dao.upsert(category.toEntity())
        return category.id
    }

    override suspend fun rename(id: String, name: String) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(name = name, updatedAt = now()).toEntity())
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(isHidden = hidden, updatedAt = now()).toEntity())
    }

    suspend fun changesSince(since: LocalDateTime): List<Category> =
        dao.changesSince(since).map { it.toDomain() }
}

class PaymentMethodRepository(
    private val dao: PaymentMethodDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : PaymentMethodStore {

    override fun observeAll(): Flow<List<PaymentMethod>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            dao.upsertAll(SeedData.paymentMethods.map { it.copy(updatedAt = now()).toEntity() })
        }
    }

    override suspend fun add(name: String): String {
        val paymentMethod = PaymentMethod(name = name, sortOrder = dao.count(), updatedAt = now())
        dao.upsert(paymentMethod.toEntity())
        return paymentMethod.id
    }

    override suspend fun rename(id: String, name: String) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(name = name, updatedAt = now()).toEntity())
    }

    override suspend fun setHidden(id: String, hidden: Boolean) {
        val existing = dao.byId(id) ?: return
        dao.upsert(existing.toDomain().copy(isHidden = hidden, updatedAt = now()).toEntity())
    }

    suspend fun changesSince(since: LocalDateTime): List<PaymentMethod> =
        dao.changesSince(since).map { it.toDomain() }
}

class BudgetRepository(
    private val dao: BudgetDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun set(categoryId: String?, monthlyLimit: Money): String {
        val scopeKey = categoryId ?: BudgetEntity.OVERALL
        val existing = dao.byScopeKey(scopeKey)
        val budget = Budget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            categoryId = categoryId,
            monthlyLimit = monthlyLimit,
            updatedAt = now(),
        )
        dao.upsert(budget.toEntity())
        return budget.id
    }

    suspend fun clear(id: String) {
        dao.softDelete(id, now())
    }

    suspend fun changesSince(since: LocalDateTime): List<Budget> =
        dao.changesSince(since).map { it.toDomain() }
}

class RecurringSeriesRepository(
    private val dao: RecurringSeriesDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeAll(): Flow<List<RecurringSeries>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun upsert(series: RecurringSeries) {
        dao.upsert(series.copy(updatedAt = now()).toEntity())
    }

    suspend fun clear(id: String) {
        dao.softDelete(id, now())
    }

    suspend fun changesSince(since: LocalDateTime): List<RecurringSeries> =
        dao.changesSince(since).map { it.toDomain() }
}
