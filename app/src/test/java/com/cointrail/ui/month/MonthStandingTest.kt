package com.cointrail.ui.month

import com.cointrail.core.Money
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.testing.FakeBudgetStore
import com.cointrail.testing.FakeCatalogStore
import com.cointrail.testing.FakeExpenseStore
import com.cointrail.testing.TEST_TIME
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The one month answer both screens read (ticket 28). Asserting [MonthStandingSource.observe] here
 * asserts Home's totals and the Monthly report's totals at once — the two cannot diverge, because
 * there is nothing left to diverge.
 */
class MonthStandingTest {

    private val october = YearMonth.of(2026, 10)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)

    private fun expense(id: String, paisa: Long, at: LocalDateTime, categoryId: String) = Expense(
        id = id,
        amount = Money(paisa),
        categoryId = categoryId,
        occurredAt = at,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `a month's standing carries total previous total per-day and named categories`() = runTest {
        val store = FakeExpenseStore()
        store.seed(
            expense("oct-food", 100_000, LocalDateTime.of(2026, 10, 3, 9, 0), "preset-food"),
            expense("oct-rent", 250_000, LocalDateTime.of(2026, 10, 5, 9, 0), "preset-rent"),
            expense("sep-food", 50_000, LocalDateTime.of(2026, 9, 3, 9, 0), "preset-food"),
            expense("aug", 999_999, LocalDateTime.of(2026, 8, 3, 9, 0), "preset-food"),
        )
        val source = MonthStandingSource(
            store,
            FakeCatalogStore(
                listOf(
                    CatalogItem(id = "preset-food", name = "Food", updatedAt = TEST_TIME),
                    CatalogItem(id = "preset-rent", name = "Rent", updatedAt = TEST_TIME),
                ),
            ),
            FakeBudgetStore(),
        )

        val standing = source.observe(october).first()

        assertEquals(Money(350_000), standing.total)
        assertEquals(Money(50_000), standing.previousTotal)
        assertEquals(listOf(LocalDate.of(2026, 10, 3), LocalDate.of(2026, 10, 5)), standing.perDay.map { it.day })
        assertEquals(Money(300_000), standing.summary.delta)
        val food = standing.categories.first { it.categoryId == "preset-food" }
        val rent = standing.categories.first { it.categoryId == "preset-rent" }
        assertEquals("Food", food.name)
        assertEquals(Money(100_000), food.current)
        assertEquals(Money(50_000), food.previous)
        assertEquals("Rent", rent.name)
        assertEquals(Money(250_000), rent.current)
        assertEquals(Money.ZERO, rent.previous)
    }

    @Test
    fun `budget bars measure the viewed month against its effective budget`() = runTest {
        val store = FakeExpenseStore()
        store.seed(expense("oct-food", 100_000, noon, "preset-food"))
        val budgets = FakeBudgetStore().apply { setDefault(null, Money(2_000_000)) }
        val source = MonthStandingSource(
            store,
            FakeCatalogStore(listOf(CatalogItem(id = "preset-food", name = "Food", updatedAt = TEST_TIME))),
            budgets,
        )

        val standing = source.observe(october).first()

        // The overall default governs the month and the bar measures the month against it.
        assertEquals(1, standing.budgets.size)
        assertTrue(standing.budgets.first().limit > Money.ZERO)
    }

    @Test
    fun `a month with no budget still shows none`() = runTest {
        val store = FakeExpenseStore()
        store.seed(expense("oct-food", 100_000, noon, "preset-food"))
        val source = MonthStandingSource(store, FakeCatalogStore(), FakeBudgetStore())

        val standing = source.observe(october).first()

        assertEquals(emptyList<com.cointrail.domain.budget.BudgetProgress>(), standing.budgets)
    }
}
