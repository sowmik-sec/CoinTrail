package com.cointrail.data.sync

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.BudgetEntity
import com.cointrail.data.db.CategoryEntity
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.db.ExpenseEntity
import com.cointrail.data.db.PaymentMethodEntity
import com.cointrail.data.db.RecurringSeriesEntity
import com.cointrail.domain.model.Expense
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The local store is what makes an offline device's data durable across a sync: it must read every
 * row including tombstones, merge the remote journal in, and persist the result atomically
 * (SPEC §7, §11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSyncLocalStoreTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var store: RoomSyncLocalStore
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 10, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        store = RoomSyncLocalStore(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun expenseEntity(id: String, paisa: Long, updatedAt: LocalDateTime, deleted: LocalDateTime? = null) =
        ExpenseEntity(
            id = id, amountPaisa = paisa, categoryId = "preset-food", note = null,
            paymentMethodId = null, occurredAt = t0, createdAt = t0, updatedAt = updatedAt, deletedAt = deleted,
        )

    private fun expense(id: String, paisa: Long, updatedAt: LocalDateTime, deleted: Boolean = false) = Expense(
        id = id, amount = Money(paisa), categoryId = "preset-food", note = null,
        paymentMethodId = null, occurredAt = t0, createdAt = t0, updatedAt = updatedAt,
        deletedAt = if (deleted) updatedAt else null,
    )

    @Test
    fun `merging an empty remote returns the local state including tombstones`() = runBlocking {
        db.expenseDao().upsert(expenseEntity("e1", 100, t1))
        db.expenseDao().upsert(expenseEntity("e2", 200, t1, deleted = t1))
        db.categoryDao().upsert(CategoryEntity("preset-food", "Food", true, false, 0, t0))
        db.paymentMethodDao().upsert(PaymentMethodEntity("pm-cash", "Cash", true, false, 0, t0))
        db.budgetDao().upsert(BudgetEntity("b1", BudgetEntity.OVERALL, null, 2_000_000, t0, null))
        db.budgetDao().upsert(BudgetEntity("b2", "preset-food", null, 500_000, t1, t1))
        db.recurringSeriesDao().upsert(
            RecurringSeriesEntity("r1", 150_000, "preset-utilities", null, null, 5, "2026-10", null, false, t1, null)
        )

        val merged = store.mergeRemote(SyncJournal.EMPTY)

        assertEquals(2, merged.expenses.size)
        assertEquals(1, merged.expenses.count { it.isDeleted })
        assertEquals(1, merged.categories.size)
        assertEquals(1, merged.paymentMethods.size)
        assertEquals(2, merged.budgets.size)
        assertEquals(1, merged.budgets.count { it.deletedAt != null })
        assertEquals(1, merged.recurring.size)
    }

    @Test
    fun `a newer remote record overwrites the local copy and is persisted`() = runBlocking {
        db.expenseDao().upsert(expenseEntity("e1", 100, t0))

        val merged = store.mergeRemote(SyncJournal(expenses = listOf(expense("e1", 200, t1))))

        assertEquals(Money(200), merged.expenses.single().amount)
        assertEquals(200L, db.expenseDao().byId("e1")!!.amountPaisa)
    }

    @Test
    fun `a local-only record survives a merge the remote does not have`() = runBlocking {
        db.expenseDao().upsert(expenseEntity("keep", 100, t1))

        val merged = store.mergeRemote(SyncJournal(expenses = listOf(expense("other", 300, t0))))

        assertEquals(setOf("keep", "other"), merged.expenses.map { it.id }.toSet())
        assertEquals(setOf("keep", "other"), db.expenseDao().all().map { it.id }.toSet())
    }

    @Test
    fun `a newer local tombstone is not resurrected by an older remote copy`() = runBlocking {
        db.expenseDao().upsert(expenseEntity("e1", 100, t1, deleted = t1))

        val merged = store.mergeRemote(SyncJournal(expenses = listOf(expense("e1", 100, t0))))

        assertTrue(merged.expenses.single().isDeleted)
        assertTrue(db.expenseDao().byId("e1")!!.deletedAt != null)
    }

    @Test
    fun `state round-trips into another namespace unchanged`() = runBlocking {
        db.expenseDao().upsert(expenseEntity("e1", 100, t1))
        db.expenseDao().upsert(expenseEntity("e2", 200, t1, deleted = t1))
        db.categoryDao().upsert(CategoryEntity("cat-1", "Pets", false, true, 9, t1))
        db.paymentMethodDao().upsert(PaymentMethodEntity("pm-bkash", "bKash", true, false, 1, t0))
        db.budgetDao().upsert(BudgetEntity("b1", BudgetEntity.OVERALL, null, 2_000_000, t0, null))
        db.recurringSeriesDao().upsert(
            RecurringSeriesEntity("r1", 150_000, "preset-utilities", "internet", "pm-bkash", 5, "2026-10", "2026-10", false, t1, null)
        )
        val original = store.mergeRemote(SyncJournal.EMPTY)

        val target = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        try {
            val applied = RoomSyncLocalStore(target).mergeRemote(original)

            assertEquals(original.sorted(), applied.sorted())
        } finally {
            target.close()
        }
    }

    private fun SyncJournal.sorted(): SyncJournal = SyncJournal(
        expenses = expenses.sortedBy { it.id },
        categories = categories.sortedBy { it.id },
        paymentMethods = paymentMethods.sortedBy { it.id },
        budgets = budgets.sortedBy { it.id },
        recurring = recurring.sortedBy { it.id },
    )
}
