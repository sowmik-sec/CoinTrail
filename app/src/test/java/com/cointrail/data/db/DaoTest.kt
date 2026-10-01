package com.cointrail.data.db

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DaoTest {

    private lateinit var db: CoinTrailDatabase
    private val t0: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)
    private val dayStart: LocalDateTime = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd: LocalDateTime = LocalDateTime.of(2026, 10, 6, 0, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expense(
        id: String,
        paisa: Long,
        category: String = "preset-food",
        at: LocalDateTime,
        deletedAt: LocalDateTime? = null,
    ) = ExpenseEntity(
        id = id,
        amountPaisa = paisa,
        categoryId = category,
        note = null,
        paymentMethodId = null,
        occurredAt = at,
        createdAt = t0,
        updatedAt = t0,
        deletedAt = deletedAt,
    )

    @Test
    fun `insert and observe range`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        db.expenseDao().upsert(expense("b", 200, at = LocalDateTime.of(2026, 10, 4, 13, 0)))
        val rows = db.expenseDao().observeBetween(dayStart, dayEnd).first()
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun `tombstoned rows are hidden from live queries`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0), deletedAt = t0))
        assertTrue(db.expenseDao().observeBetween(dayStart, dayEnd).first().isEmpty())
    }

    @Test
    fun `softDelete stamps deletedAt and updatedAt`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        val now = LocalDateTime.of(2026, 10, 5, 22, 0)
        db.expenseDao().softDelete("a", now)
        val row = db.expenseDao().byId("a")!!
        assertEquals(now, row.deletedAt)
        assertEquals(now, row.updatedAt)
    }

    @Test
    fun `changesSince returns rows including tombstones`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 13, 0)))
        db.expenseDao().softDelete("a", t0.plusDays(1))
        assertEquals(listOf("a"), db.expenseDao().changesSince(t0).map { it.id })
        assertTrue(db.expenseDao().changesSince(LocalDateTime.of(2027, 1, 1, 0, 0)).isEmpty())
    }

    @Test
    fun `daily totals group by day and stay in range`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, at = LocalDateTime.of(2026, 10, 5, 20, 0)))
        db.expenseDao().upsert(expense("c", 400, at = LocalDateTime.of(2026, 10, 3, 8, 0)))
        db.expenseDao().upsert(expense("d", 999, at = LocalDateTime.of(2026, 11, 5, 8, 0)))
        val rows = db.expenseDao().observeDailyTotals(
            LocalDateTime.of(2026, 10, 1, 0, 0),
            LocalDateTime.of(2026, 11, 1, 0, 0),
        ).first()
        assertEquals(listOf("2026-10-03", "2026-10-05"), rows.map { it.day })
        assertEquals(350L, rows[1].totalPaisa)
    }

    @Test
    fun `category totals group by category ordered by total desc`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, category = "preset-food", at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, category = "preset-food", at = LocalDateTime.of(2026, 10, 5, 10, 0)))
        db.expenseDao().upsert(expense("c", 400, category = "preset-transport", at = LocalDateTime.of(2026, 10, 5, 11, 0)))
        val rows = db.expenseDao().observeCategoryTotals(dayStart, dayEnd).first()
        assertEquals(listOf("preset-transport", "preset-food"), rows.map { it.categoryId })
        assertEquals(350L, rows[1].totalPaisa)
    }

    @Test
    fun `total between sums paisa`() = runBlocking {
        db.expenseDao().upsert(expense("a", 100, at = LocalDateTime.of(2026, 10, 5, 9, 0)))
        db.expenseDao().upsert(expense("b", 250, at = LocalDateTime.of(2026, 10, 5, 10, 0)))
        assertEquals(350L, db.expenseDao().observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `budget upsert on same scopeKey replaces the row`() = runBlocking {
        db.budgetDao().upsert(BudgetEntity("b1", BudgetEntity.OVERALL, 1000, t0, null))
        db.budgetDao().upsert(BudgetEntity("b2", BudgetEntity.OVERALL, 2000, t0, null))
        val all = db.budgetDao().observeAll().first()
        assertEquals(1, all.size)
        assertEquals("b2", all.first().id)
        assertEquals(2000L, all.first().monthlyLimitPaisa)
    }

    @Test
    fun `budget softDelete hides row and tombstone stays in changesSince`() = runBlocking {
        db.budgetDao().upsert(BudgetEntity("b1", "preset-food", 1000, t0, null))
        val now = LocalDateTime.of(2026, 10, 5, 22, 0)
        db.budgetDao().softDelete("b1", now)
        assertTrue(db.budgetDao().observeAll().first().isEmpty())
        val changes = db.budgetDao().changesSince(t0)
        assertEquals(1, changes.size)
        assertEquals(now, changes.first().deletedAt)
    }

    @Test
    fun `category rows round-trip and count works`() = runBlocking {
        val dao = db.categoryDao()
        dao.upsert(CategoryEntity("preset-food", "Food", true, false, 0, t0))
        dao.upsert(CategoryEntity("c1", "Pets", false, true, 9, t0))
        assertEquals(2, dao.count())
        assertEquals(listOf("preset-food", "c1"), dao.observeAll().first().map { it.id })
        assertEquals("Pets", dao.byId("c1")!!.name)
        assertNull(dao.byId("missing"))
    }
}
