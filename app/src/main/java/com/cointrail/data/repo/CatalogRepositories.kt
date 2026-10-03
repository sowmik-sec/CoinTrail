package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.SeedData
import com.cointrail.data.db.BudgetDao
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
import java.time.YearMonth
import java.util.UUID

class CategoryRepository(
    private val dao: CategoryDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : CategoryStore {

    override fun observeAll(): Flow<List<Category>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    suspend fun ensureSeeded() {
        if (dao.count() == 0) {
            // Seeded with SeedData's fixed timestamp, not "now": a fresh device must not out-stamp a
            // preset rename or hide synced from another device in last-write-wins (SPEC §7).
            dao.upsertAll(SeedData.categories.map { it.toEntity() })
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
            // Fixed seed timestamp, as with categories, so presets do not out-stamp synced edits.
            dao.upsertAll(SeedData.paymentMethods.map { it.toEntity() })
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
) : BudgetStore {

    override fun observeAll(): Flow<List<Budget>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setDefault(categoryId: String?, monthlyLimit: Money): String =
        upsertScopeMonth(categoryId, month = null, monthlyLimit = monthlyLimit)

    override suspend fun setOverride(categoryId: String?, month: YearMonth, monthlyLimit: Money): String =
        upsertScopeMonth(categoryId, month = month, monthlyLimit = monthlyLimit)

    override suspend fun setNoBudget(categoryId: String?, month: YearMonth): String =
        upsertScopeMonth(categoryId, month = month, monthlyLimit = null)

    override suspend fun removeOverride(categoryId: String?, month: YearMonth) {
        val existing = dao.byScopeAndMonth(scopeKey(categoryId), month.toString()) ?: return
        // The tombstone is the "override removed" marker; only a live row still needs one.
        if (existing.deletedAt != null) return
        dao.softDelete(existing.id, now())
    }

    override suspend fun clear(id: String) {
        dao.softDelete(id, now())
    }

    /**
     * Writes one (scope, month) row, reusing whatever row already exists for the key — a live one
     * to replace it, a tombstoned one to bring it back — so the unique (scope, month) key always
     * holds exactly one row and its sync history keeps its id.
     */
    private suspend fun upsertScopeMonth(categoryId: String?, month: YearMonth?, monthlyLimit: Money?): String {
        val existing = dao.byScopeAndMonth(scopeKey(categoryId), month?.toString())
        val budget = Budget(
            id = existing?.id ?: UUID.randomUUID().toString(),
            categoryId = categoryId,
            month = month,
            monthlyLimit = monthlyLimit,
            updatedAt = now(),
        )
        dao.upsert(budget.toEntity())
        return budget.id
    }

    private fun scopeKey(categoryId: String?): String = categoryId ?: Budget.OVERALL_SCOPE

    suspend fun changesSince(since: LocalDateTime): List<Budget> =
        dao.changesSince(since).map { it.toDomain() }
}

class RecurringSeriesRepository(
    private val dao: RecurringSeriesDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : RecurringStore {

    override fun observeAll(): Flow<List<RecurringSeries>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun add(
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
        startMonth: YearMonth,
    ): String {
        val series = RecurringSeries(
            amount = amount,
            categoryId = categoryId,
            note = note,
            paymentMethodId = paymentMethodId,
            dayOfMonth = dayOfMonth,
            startMonth = startMonth,
            updatedAt = now(),
        )
        dao.upsert(series.toEntity())
        return series.id
    }

    override suspend fun update(
        id: String,
        amount: Money,
        categoryId: String,
        note: String?,
        paymentMethodId: String?,
        dayOfMonth: Int,
    ) {
        val existing = dao.byId(id)?.toDomain() ?: return
        dao.upsert(
            existing.copy(
                amount = amount,
                categoryId = categoryId,
                note = note,
                paymentMethodId = paymentMethodId,
                dayOfMonth = dayOfMonth,
                updatedAt = now(),
            ).toEntity()
        )
    }

    override suspend fun setPaused(id: String, paused: Boolean) {
        val existing = dao.byId(id)?.toDomain() ?: return
        dao.upsert(existing.copy(isPaused = paused, updatedAt = now()).toEntity())
    }

    override suspend fun delete(id: String) {
        dao.softDelete(id, now())
    }

    /** Full-row upsert, used by tests to write a series verbatim. */
    suspend fun upsert(series: RecurringSeries) {
        dao.upsert(series.copy(updatedAt = now()).toEntity())
    }

    suspend fun changesSince(since: LocalDateTime): List<RecurringSeries> =
        dao.changesSince(since).map { it.toDomain() }
}
