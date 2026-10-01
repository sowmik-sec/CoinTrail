package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpenseRepositoryTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var repo: ExpenseRepository
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 30)
    private val dayStart: LocalDateTime = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd: LocalDateTime = LocalDateTime.of(2026, 10, 6, 0, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        repo = ExpenseRepository(db.expenseDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `added expense appears in range and total`() = runBlocking {
        val id = repo.add(Money(125_050), "preset-food", "lunch", "pm-cash", LocalDateTime.of(2026, 10, 5, 13, 0))

        val day = repo.observeBetween(dayStart, dayEnd).first()
        assertEquals(listOf(id), day.map { it.id })
        assertEquals(Money(125_050), day.first().amount)
        assertEquals(Money(125_050), repo.observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `range excludes other days`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 4, 13, 0))
        assertTrue(repo.observeBetween(dayStart, dayEnd).first().isEmpty())
    }

    @Test
    fun `update replaces amount`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 13, 0))
        val expense = repo.observeBetween(dayStart, dayEnd).first().first()

        repo.update(expense.copy(amount = Money(250)))

        assertEquals(Money(250), repo.observeTotalBetween(dayStart, dayEnd).first())
    }

    @Test
    fun `soft delete hides row but keeps tombstone for sync`() = runBlocking {
        val id = repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 13, 0))

        repo.delete(id)

        assertTrue(repo.observeBetween(dayStart, dayEnd).first().isEmpty())
        val changes = repo.changesSince(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(1, changes.size)
        assertNotNull(changes.first().deletedAt)
    }

    @Test
    fun `restore undoes a delete and brings the row back untouched`() = runBlocking {
        val id = repo.add(Money(100), "preset-food", "lunch", "pm-cash", LocalDateTime.of(2026, 10, 5, 13, 0))
        repo.delete(id)

        repo.restore(id)

        val day = repo.observeBetween(dayStart, dayEnd).first()
        assertEquals(listOf(id), day.map { it.id })
        assertEquals(Money(100), repo.observeTotalBetween(dayStart, dayEnd).first())
        assertEquals("lunch", day.single().note)
        assertEquals("pm-cash", day.single().paymentMethodId)
        assertNull(repo.findById(id)?.deletedAt)
    }

    @Test
    fun `update edits every field including the datetime`() = runBlocking {
        val id = repo.add(Money(100), "preset-food", "old", "pm-cash", LocalDateTime.of(2026, 10, 5, 13, 0))
        val original = repo.findById(id)!!
        val moved = LocalDateTime.of(2026, 10, 2, 9, 30)

        repo.update(
            original.copy(
                amount = Money(250),
                categoryId = "preset-transport",
                note = "new",
                paymentMethodId = null,
                occurredAt = moved,
            )
        )

        val edited = repo.findById(id)!!
        assertEquals(Money(250), edited.amount)
        assertEquals("preset-transport", edited.categoryId)
        assertEquals("new", edited.note)
        assertNull(edited.paymentMethodId)
        assertEquals(moved, edited.occurredAt)
        assertTrue(repo.observeBetween(dayStart, dayEnd).first().isEmpty())
    }

    @Test
    fun `find by id returns the expense or null`() = runBlocking {
        val id = repo.add(Money(100), "preset-food", "note", null, LocalDateTime.of(2026, 10, 5, 13, 0))

        assertEquals(Money(100), repo.findById(id)?.amount)
        assertEquals("note", repo.findById(id)?.note)
        assertNull(repo.findById("missing"))
    }

    @Test
    fun `daily totals group by day within month`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 1, 9, 0))
        repo.add(Money(250), "preset-food", null, null, LocalDateTime.of(2026, 10, 1, 20, 0))
        repo.add(Money(400), "preset-transport", null, null, LocalDateTime.of(2026, 10, 3, 8, 0))
        repo.add(Money(999), "preset-food", null, null, LocalDateTime.of(2026, 11, 1, 8, 0))

        val totals = repo.observeDailyTotals(YearMonth.of(2026, 10)).first()

        assertEquals(2, totals.size)
        assertEquals(LocalDate.of(2026, 10, 1), totals[0].day)
        assertEquals(Money(350), totals[0].total)
        assertEquals(LocalDate.of(2026, 10, 3), totals[1].day)
        assertEquals(Money(400), totals[1].total)
    }

    @Test
    fun `category totals sum per category ordered by total desc`() = runBlocking {
        repo.add(Money(100), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 9, 0))
        repo.add(Money(250), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 10, 0))
        repo.add(Money(400), "preset-transport", null, null, LocalDateTime.of(2026, 10, 5, 11, 0))

        val totals = repo.observeCategoryTotals(dayStart, dayEnd).first()

        assertEquals(2, totals.size)
        assertEquals("preset-transport", totals[0].categoryId)
        assertEquals(Money(400), totals[0].total)
        assertEquals("preset-food", totals[1].categoryId)
        assertEquals(Money(350), totals[1].total)
    }
}
