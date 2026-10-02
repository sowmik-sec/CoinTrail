package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.recurring.RecurringExpenseGenerator
import com.cointrail.domain.model.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class RecurringExpenseGeneratorTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var series: RecurringSeriesRepository
    private lateinit var generator: RecurringExpenseGenerator
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 15, 9, 0)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        series = RecurringSeriesRepository(db.recurringSeriesDao()) { now }
        generator = RecurringExpenseGenerator(db.recurringSeriesDao(), db.expenseDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun newSeries(
        dayOfMonth: Int = 15,
        startMonth: YearMonth = YearMonth.of(2026, 10),
        amount: Money = Money(150_000),
    ): String = runBlocking {
        series.add(
            amount = amount,
            categoryId = "preset-rent",
            note = "rent",
            paymentMethodId = "pm-bkash",
            dayOfMonth = dayOfMonth,
            startMonth = startMonth,
        )
    }

    private fun expensesOn(date: LocalDate): List<Expense> = runBlocking {
        db.expenseDao()
            .observeBetween(date.atStartOfDay(), date.plusDays(1).atStartOfDay())
            .first()
            .map { it.toDomain() }
    }

    @Test
    fun `generates an ordinary expense on the day of month`() = runBlocking {
        newSeries(dayOfMonth = 15)

        assertEquals(1, generator.generateDue(LocalDate.of(2026, 10, 15)))

        val expense = expensesOn(LocalDate.of(2026, 10, 15)).single()
        assertEquals(Money(150_000), expense.amount)
        assertEquals("preset-rent", expense.categoryId)
        assertEquals("rent", expense.note)
        assertEquals("pm-bkash", expense.paymentMethodId)
        assertEquals(YearMonth.of(2026, 10), series.observeAll().first().single().lastGeneratedMonth)
    }

    @Test
    fun `does not generate before the day has arrived`() = runBlocking {
        newSeries(dayOfMonth = 15)

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 14)))

        assertTrue(expensesOn(LocalDate.of(2026, 10, 15)).isEmpty())
        assertNull(series.observeAll().first().single().lastGeneratedMonth)
    }

    @Test
    fun `never duplicates a month's entry`() = runBlocking {
        newSeries(dayOfMonth = 15)

        assertEquals(1, generator.generateDue(LocalDate.of(2026, 10, 15)))
        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 20)))

        assertEquals(1, expensesOn(LocalDate.of(2026, 10, 15)).size)
    }

    @Test
    fun `day 31 clamps to the month's end`() = runBlocking {
        newSeries(dayOfMonth = 31, startMonth = YearMonth.of(2026, 2))

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 2, 27)))
        assertEquals(1, generator.generateDue(LocalDate.of(2026, 2, 28)))

        assertEquals(1, expensesOn(LocalDate.of(2026, 2, 28)).size)
    }

    @Test
    fun `editing a generated occurrence survives regeneration`() = runBlocking {
        newSeries()
        generator.generateDue(LocalDate.of(2026, 10, 15))
        val expense = expensesOn(LocalDate.of(2026, 10, 15)).single()

        db.expenseDao().upsert(expense.copy(amount = Money(99_900)).toEntity())

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 20)))
        assertEquals(Money(99_900), expensesOn(LocalDate.of(2026, 10, 15)).single().amount)
    }

    @Test
    fun `deleting a generated occurrence leaves the series and next month alone`() = runBlocking {
        newSeries()
        generator.generateDue(LocalDate.of(2026, 10, 15))
        val id = expensesOn(LocalDate.of(2026, 10, 15)).single().id

        db.expenseDao().softDelete(id, now)

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 20)))
        val row = series.observeAll().first().single()
        assertFalse(row.isPaused)
        assertEquals(YearMonth.of(2026, 10), row.lastGeneratedMonth)

        assertEquals(1, generator.generateDue(LocalDate.of(2026, 11, 15)))
        assertEquals(1, expensesOn(LocalDate.of(2026, 11, 15)).size)
    }

    @Test
    fun `a deleted occurrence is not resurrected on a later run`() = runBlocking {
        newSeries()
        generator.generateDue(LocalDate.of(2026, 10, 15))
        val expense = expensesOn(LocalDate.of(2026, 10, 15)).single()
        db.expenseDao().softDelete(expense.id, now)

        val row = series.observeAll().first().single()
        series.upsert(row.copy(lastGeneratedMonth = null))

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 20)))
        assertTrue(expensesOn(LocalDate.of(2026, 10, 15)).isEmpty())
    }

    @Test
    fun `deleting a series stops generation but keeps its expenses`() = runBlocking {
        val id = newSeries()
        generator.generateDue(LocalDate.of(2026, 10, 15))
        val generatedId = expensesOn(LocalDate.of(2026, 10, 15)).single().id

        series.delete(id)

        assertTrue(series.observeAll().first().isEmpty())
        assertEquals(0, generator.generateDue(LocalDate.of(2026, 11, 15)))
        val kept = db.expenseDao().byId(generatedId)
        assertNotNull(kept)
        assertNull(kept!!.deletedAt)
    }

    @Test
    fun `a paused series does not generate`() = runBlocking {
        val id = newSeries()
        series.setPaused(id, true)

        assertEquals(0, generator.generateDue(LocalDate.of(2026, 10, 20)))
        assertTrue(expensesOn(LocalDate.of(2026, 10, 15)).isEmpty())
    }

    @Test
    fun `resuming generates the current month`() = runBlocking {
        val id = newSeries()
        series.setPaused(id, true)
        series.setPaused(id, false)

        assertEquals(1, generator.generateDue(LocalDate.of(2026, 10, 20)))
        assertEquals(1, expensesOn(LocalDate.of(2026, 10, 15)).size)
    }

    @Test
    fun `generates each series independently`() = runBlocking {
        newSeries(dayOfMonth = 15)
        newSeries(dayOfMonth = 10, amount = Money(50_000))

        assertEquals(2, generator.generateDue(LocalDate.of(2026, 10, 20)))
    }
}
