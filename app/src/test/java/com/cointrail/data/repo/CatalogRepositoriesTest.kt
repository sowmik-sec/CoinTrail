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
import java.time.LocalDateTime
import java.time.YearMonth

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
    fun `ensureSeeded inserts category presets exactly once`() = runBlocking {
        categories.ensureSeeded()
        categories.ensureSeeded()

        val all = categories.observeAll().first()

        assertEquals(
            listOf(
                "preset-food" to "Food",
                "preset-groceries" to "Groceries",
                "preset-transport" to "Transport",
                "preset-utilities" to "Utilities",
                "preset-rent" to "Rent",
                "preset-health" to "Health",
                "preset-shopping" to "Shopping",
                "preset-entertainment" to "Entertainment",
                "preset-other" to "Other",
            ),
            all.map { it.id to it.name },
        )
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 7, 8), all.map { it.sortOrder })
        assertTrue(all.all { it.isPreset })
    }

    @Test
    fun `ensureSeeded inserts payment method presets`() = runBlocking {
        paymentMethods.ensureSeeded()

        val all = paymentMethods.observeAll().first()

        assertEquals(
            listOf(
                "pm-cash" to "Cash",
                "pm-bkash" to "bKash",
                "pm-nagad" to "Nagad",
                "pm-card" to "Card",
            ),
            all.map { it.id to it.name },
        )
        assertTrue(all.all { it.isPreset })
    }

    @Test
    fun `add rename and hide category`() = runBlocking {
        val id = categories.add("Pets")

        categories.rename(id, "Pet care")
        categories.setHidden(id, true)

        val pets = categories.observeAll().first().first { it.id == id }
        assertEquals("Pet care", pets.name)
        assertTrue(pets.isHidden)
    }

    @Test
    fun `a custom category is appended after the presets`() = runBlocking {
        categories.ensureSeeded()

        categories.add("Pets")

        val all = categories.observeAll().first()
        assertEquals("Pets", all.last().name)
        assertEquals(9, all.last().sortOrder)
    }

    @Test
    fun `a custom payment method is appended after the presets`() = runBlocking {
        paymentMethods.ensureSeeded()

        paymentMethods.add("Wallet")

        val all = paymentMethods.observeAll().first()
        assertEquals("Wallet", all.last().name)
        assertEquals(4, all.last().sortOrder)
    }

    @Test
    fun `overall budget round-trips null categoryId and clear tombstones`() = runBlocking {
        budgets.set(null, Money(2_000_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertNull(all.first().categoryId)
        assertEquals(Money(2_000_000), all.first().monthlyLimit)

        budgets.clear(all.first().id)
        assertTrue(budgets.observeAll().first().isEmpty())
    }

    @Test
    fun `setting same scope twice replaces the limit`() = runBlocking {
        budgets.set("preset-food", Money(500_000))
        budgets.set("preset-food", Money(600_000))

        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(Money(600_000), all.first().monthlyLimit)
    }

    @Test
    fun `re-setting a cleared budget reuses its row instead of dropping the tombstone`() = runBlocking {
        val first = budgets.set("preset-food", Money(500_000))
        budgets.clear(first)

        val second = budgets.set("preset-food", Money(700_000))

        assertEquals(first, second)
        val all = budgets.observeAll().first()
        assertEquals(1, all.size)
        assertEquals(Money(700_000), all.first().monthlyLimit)
        val changes = budgets.changesSince(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(1, changes.size)
        assertNull(changes.first().deletedAt)
    }

    @Test
    fun `recurring series round-trips and delete tombstones it`() = runBlocking {
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
        val changes = recurring.changesSince(LocalDateTime.of(2026, 1, 1, 0, 0))
        assertEquals(1, changes.size)
        assertNotNull(changes.first().deletedAt)
    }
}
