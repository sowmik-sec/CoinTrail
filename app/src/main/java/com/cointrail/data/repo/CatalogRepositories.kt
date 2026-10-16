package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.SeedData
import com.cointrail.data.db.BudgetDao
import com.cointrail.data.db.CatalogDao
import com.cointrail.data.db.RecurringSeriesDao
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

/**
 * The one catalog store implementation, serving both lists (ticket 25): both physical tables behind
 * one [CatalogDao] and one row concept, keyed by [CatalogKind]. Hidden items are returned (the
 * manage screens toggle them); pickers take a list's [visible] rows.
 */
class CatalogRepository(
    private val dao: CatalogDao,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : CatalogStore {

    override fun observeAll(kind: CatalogKind): Flow<List<CatalogItem>> = when (kind) {
        CatalogKind.CATEGORIES -> dao.observeAllCategories().map { rows -> rows.map { it.toCatalogItem() } }
        CatalogKind.PAYMENT_METHODS ->
            dao.observeAllPaymentMethods().map { rows -> rows.map { it.toCatalogItem() } }
    }

    /**
     * Seeds the kind's presets exactly once, with [SeedData]'s fixed timestamp rather than "now": a
     * fresh device must not out-stamp a preset rename or hide synced from another device in
     * last-write-wins (SPEC §7).
     */
    override suspend fun ensureSeeded(kind: CatalogKind) {
        val presets = when (kind) {
            CatalogKind.CATEGORIES -> SeedData.categories
            CatalogKind.PAYMENT_METHODS -> SeedData.paymentMethods
        }
        if (count(kind) == 0) {
            write(kind, presets)
        }
    }

    override suspend fun add(kind: CatalogKind, name: String): String {
        val item = CatalogItem(name = name, sortOrder = count(kind), updatedAt = now())
        write(kind, listOf(item))
        return item.id
    }

    override suspend fun rename(kind: CatalogKind, id: String, name: String) {
        val existing = byId(kind, id) ?: return
        write(kind, listOf(existing.copy(name = name, updatedAt = now())))
    }

    override suspend fun setHidden(kind: CatalogKind, id: String, hidden: Boolean) {
        val existing = byId(kind, id) ?: return
        write(kind, listOf(existing.copy(isHidden = hidden, updatedAt = now())))
    }

    private suspend fun count(kind: CatalogKind): Int = when (kind) {
        CatalogKind.CATEGORIES -> dao.categoryCount()
        CatalogKind.PAYMENT_METHODS -> dao.paymentMethodCount()
    }

    private suspend fun byId(kind: CatalogKind, id: String): CatalogItem? = when (kind) {
        CatalogKind.CATEGORIES -> dao.categoryById(id)?.toCatalogItem()
        CatalogKind.PAYMENT_METHODS -> dao.paymentMethodById(id)?.toCatalogItem()
    }

    private suspend fun write(kind: CatalogKind, rows: List<CatalogItem>) = when (kind) {
        CatalogKind.CATEGORIES -> dao.upsertAllCategories(rows.map { it.toCategoryEntity() })
        CatalogKind.PAYMENT_METHODS -> dao.upsertAllPaymentMethods(rows.map { it.toCatalogItemEntity() })
    }
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
}
