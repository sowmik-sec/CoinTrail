package com.cointrail.data.repo

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.SeedData
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
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The budget and recurring repositories behind their stores. The one catalog store over the two
 * catalog repositories lives in CatalogStoreTest; this file keeps only what those tests do not
 * assert — the budget tri-state rows and the recurring series lifecycle.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CatalogRepositoriesTest {

    private lateinit var db: CoinTrailDatabase
    private lateinit var categories: CategoryRepository
    private lateinit var paymentMethods: PaymentMethodRepository
    private lateinit var budgets: BudgetRepository
    private lateinit var recurring: RecurringSeriesRepository
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 30)

    @Before
    fun setUp() {
        db = CoinTrailDatabase.createInMemory(ApplicationProvider.getApplicationContext())
        categories = CategoryRepository(db.categoryDao()) { now }
        paymentMethods = PaymentMethodRepository(db.paymentMethodDao()) { now }
        budgets = BudgetRepository(db.budgetDao()) { now }
        recurring = RecurringSeriesRepository(db.recurringSeriesDao()) { now }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `seeding keeps the fixed preset timestamp so synced edits win`() = runBlocking {
        categories.ensureSeeded()
        paymentMethods.ensureSeeded()

        val food = categories.observeAll().first().first { it.id == "preset-food" }
        val cash = paymentMethods.observeAll().first().first { it.id == "pm-cash" }

        assertEquals(SeedData.categories.first { it.id == "preset-food" }.updatedAt, food.updatedAt)
        assertEquals(SeedData.paymentMethods.first { it.id == "pm-cash" }.updatedAt, cash.updatedAt)
    }

    @Test
    fun `overall budget round-trips null categoryId and clear tombstones`() = runBlocking {
        budgets.setDefault(null, Money(2_000_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertNull(all.first().categoryId)
        assertEquals(Money(2_000_000), all.first().monthlyLimit)

        budgets.clear(all.first().id)
        assertTrue(budgets.observeAll().first().isEmpty())
    }

    @Test
    fun `setting same scope twice replaces the limit`() = runBlocking {
        budgets.setDefault("preset-food", Money(500_000))
        budgets.setDefault("preset-food", Money(600_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(Money(600_000), all.first().monthlyLimit)
    }

    @Test
    fun `re-setting a cleared budget reuses its row instead of dropping the tombstone`() = runBlocking {
        val first = budgets.setDefault("preset-food", Money(500_000))
        budgets.clear(first)

        val second = budgets.setDefault("preset-food", Money(700_000))

        assertEquals(first, second)
        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(Money(700_000), all.first().monthlyLimit)
    }

    @Test
    fun `a month override coexists with the default and replaces only its own month`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        val november = YearMonth.of(2026, 11)
        budgets.setDefault("preset-food", Money(500_000))

        val overrideId = budgets.setOverride("preset-food", october, Money(900_000))
        budgets.setOverride("preset-food", november, Money(400_000))

        val all = budgets.observeAll().first().sortedBy { it.month }
        assertEquals(3, all.size)
        val octoberRow = all.first { it.month == october }
        assertEquals(overrideId, octoberRow.id)
        assertEquals(Money(900_000), octoberRow.monthlyLimit)
        assertEquals(Money(500_000), all.first { it.month == null }.monthlyLimit)
    }

    @Test
    fun `setting an override twice replaces the month's row`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        budgets.setDefault("preset-food", Money(500_000))

        budgets.setOverride("preset-food", october, Money(900_000))
        budgets.setOverride("preset-food", october, Money(800_000))

        val all = budgets.observeAll().first()
        assertEquals(2, all.size)
        assertEquals(Money(800_000), all.first { it.month == october }.monthlyLimit)
    }

    @Test
    fun `setNoBudget stores the explicit null-limit row for the month`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        budgets.setDefault(null, Money(2_000_000))

        budgets.setNoBudget("preset-food", october)

        val row = budgets.observeAll().first().first { it.categoryId == "preset-food" }
        assertEquals(october, row.month)
        assertNull(row.monthlyLimit)
    }

    @Test
    fun `removeOverride returns the month to the default`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        budgets.setDefault("preset-food", Money(500_000))
        budgets.setOverride("preset-food", october, Money(900_000))

        budgets.removeOverride("preset-food", october)

        val live = budgets.observeAll().first()
        assertEquals(1, live.size)
        assertNull(live.single().month)
    }

    @Test
    fun `removeOverride on the explicit no-budget row also returns the month to the default`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        budgets.setDefault(null, Money(2_000_000))
        budgets.setNoBudget("preset-food", october)

        budgets.removeOverride("preset-food", october)

        val live = budgets.observeAll().first()
        assertEquals(1, live.size)
        assertEquals(null, live.single().categoryId)
    }

    @Test
    fun `removeOverride without any override is a no-op`() = runBlocking {
        budgets.setDefault("preset-food", Money(500_000))

        budgets.removeOverride("preset-food", YearMonth.of(2026, 10))
        budgets.removeOverride("preset-transport", YearMonth.of(2026, 10))

        assertEquals(1, budgets.observeAll().first().size)
    }

    @Test
    fun `re-setting a removed override revives its row instead of duplicating it`() = runBlocking {
        val october = YearMonth.of(2026, 10)
        budgets.setDefault("preset-food", Money(500_000))
        val first = budgets.setOverride("preset-food", october, Money(900_000))
        budgets.removeOverride("preset-food", october)

        val second = budgets.setOverride("preset-food", october, Money(700_000))

        assertEquals(first, second)
        val rows = budgets.observeAll().first()
        assertEquals(2, rows.size)
        assertEquals(Money(700_000), rows.first { it.month == october }.monthlyLimit)
    }

    @Test
    fun `recurring series round-trips and delete hides it`() = runBlocking {
        val id = recurring.add(
            amount = Money(150_000),
            categoryId = "preset-utilities",
            note = "internet",
            paymentMethodId = "pm-bkash",
            dayOfMonth = 5,
            startMonth = YearMonth.of(2026, 10),
        )

        val created = recurring.observeAll().first().single()
        assertEquals(id, created.id)
        assertEquals(Money(150_000), created.amount)
        assertEquals(5, created.dayOfMonth)

        recurring.setPaused(id, true)
        assertTrue(recurring.observeAll().first().single().isPaused)

        recurring.update(id, Money(175_000), "preset-rent", null, null, 10)
        val edited = recurring.observeAll().first().single()
        assertEquals(Money(175_000), edited.amount)
        assertEquals("preset-rent", edited.categoryId)
        assertEquals(10, edited.dayOfMonth)
        assertTrue(edited.isPaused)

        recurring.delete(id)

        assertTrue(recurring.observeAll().first().isEmpty())
    }
}
