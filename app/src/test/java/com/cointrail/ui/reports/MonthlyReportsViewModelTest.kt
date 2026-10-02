package com.cointrail.ui.reports

import com.cointrail.core.Money
import com.cointrail.domain.budget.BudgetStatus
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.testing.FakeBudgetStore
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.FakeExpenseStore
import com.cointrail.testing.FakePaymentMethodStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val month: YearMonth = YearMonth.of(2026, 10)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val rent = Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)
    private val transport = Category(id = "preset-transport", name = "Transport", isPreset = true, updatedAt = noon)
    private val cash = PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, updatedAt = noon)

    private fun expense(
        id: String,
        paisa: Long,
        at: LocalDateTime,
        categoryId: String = "preset-food",
        note: String? = null,
        paymentMethodId: String? = null,
    ) = Expense(
        id = id,
        amount = Money(paisa),
        categoryId = categoryId,
        note = note,
        paymentMethodId = paymentMethodId,
        occurredAt = at,
        createdAt = at,
        updatedAt = at,
    )

    private fun budget(id: String, categoryId: String?, paisa: Long) =
        Budget(id = id, categoryId = categoryId, monthlyLimit = Money(paisa), updatedAt = noon)

    private fun viewModel(
        store: FakeExpenseStore,
        budgets: FakeBudgetStore = FakeBudgetStore(),
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food, rent, transport)),
        paymentMethods: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash)),
        initialMonth: YearMonth = month,
    ) = MonthlyReportsViewModel(
        expenses = store,
        categories = categories,
        paymentMethods = paymentMethods,
        budgets = budgets,
        initialMonth = initialMonth,
    )

    @Test
    fun `the heatmap maps each day of the month to its total`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("a", 10_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("b", 5_000, LocalDateTime.of(2026, 10, 3, 20, 0)),
            expense("c", 7_500, LocalDateTime.of(2026, 10, 15, 12, 0)),
        )

        val totals = viewModel(store).state.value.dailyTotals

        assertEquals(Money(15_000), totals[LocalDate.of(2026, 10, 3)])
        assertEquals(Money(7_500), totals[LocalDate.of(2026, 10, 15)])
        assertNull(totals[LocalDate.of(2026, 10, 4)])
    }

    @Test
    fun `the monthly total only counts spending inside the month`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("in", 40_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("prev", 999_999, LocalDateTime.of(2026, 9, 30, 9, 0)),
            expense("next", 888_888, LocalDateTime.of(2026, 11, 1, 9, 0)),
        )

        assertEquals(Money(40_000), viewModel(store).state.value.total)
    }

    @Test
    fun `the breakdown lists categories that spent, biggest first, with shares`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("food", 30_000, LocalDateTime.of(2026, 10, 3, 9, 0), categoryId = "preset-food"),
            expense("rent", 50_000, LocalDateTime.of(2026, 10, 4, 9, 0), categoryId = "preset-rent"),
            expense("bus", 20_000, LocalDateTime.of(2026, 10, 5, 9, 0), categoryId = "preset-transport"),
        )

        val breakdown = viewModel(store).state.value.breakdown

        assertEquals(listOf("Rent", "Food", "Transport"), breakdown.map { it.name })
        assertEquals(Money(50_000), breakdown[0].amount)
        assertEquals(50, breakdown[0].sharePercent)
        assertEquals(30, breakdown[1].sharePercent)
        assertEquals(1.0f, breakdown[0].barFraction, 0.001f)
        assertEquals(0.6f, breakdown[1].barFraction, 0.001f)
        assertEquals(0.4f, breakdown[2].barFraction, 0.001f)
    }

    @Test
    fun `a category with no spending this month is left out of the breakdown but stays in the comparison`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("food", 30_000, LocalDateTime.of(2026, 10, 3, 9, 0), categoryId = "preset-food"),
            expense("last-rent", 50_000, LocalDateTime.of(2026, 9, 3, 9, 0), categoryId = "preset-rent"),
        )

        val state = viewModel(store).state.value

        assertEquals(listOf("Food"), state.breakdown.map { it.name })
        assertTrue(state.comparisons.any { it.name == "Rent" })
    }

    @Test
    fun `the comparison carries the previous month's total and the total delta`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("this", 40_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("last", 25_000, LocalDateTime.of(2026, 9, 3, 9, 0)),
        )

        val state = viewModel(store).state.value

        assertEquals(Money(40_000), state.total)
        assertEquals(Money(25_000), state.previousTotal)
        assertEquals(Money(15_000), state.delta)
    }

    @Test
    fun `per-category deltas resolve category names`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("this", 40_000, LocalDateTime.of(2026, 10, 3, 9, 0), categoryId = "preset-food"),
            expense("last", 10_000, LocalDateTime.of(2026, 9, 3, 9, 0), categoryId = "preset-food"),
            expense("old-rent", 50_000, LocalDateTime.of(2026, 9, 4, 9, 0), categoryId = "preset-rent"),
        )

        val comparisons = viewModel(store).state.value.comparisons.associateBy { it.name }

        assertEquals(Money(30_000), comparisons.getValue("Food").delta)
        assertEquals(Money(-50_000), comparisons.getValue("Rent").delta)
    }

    @Test
    fun `budget bars pair the overall budget with the month and per-category budgets with their category`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("food", 30_000, LocalDateTime.of(2026, 10, 3, 9, 0), categoryId = "preset-food"),
            expense("rent", 50_000, LocalDateTime.of(2026, 10, 4, 9, 0), categoryId = "preset-rent"),
        )
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("overall", null, 200_000),
            budget("food-budget", "preset-food", 100_000),
        )

        val bars = viewModel(store, budgets).state.value.budgets

        assertEquals(listOf("Overall", "Food"), bars.map { it.label })
        assertEquals(Money(80_000), bars[0].spent)
        assertEquals(Money(30_000), bars[1].spent)
        assertEquals(BudgetStatus.ON_TRACK, bars[1].status)
    }

    @Test
    fun `there are no budget bars when no budgets are set`() {
        val store = FakeExpenseStore()
        store.seed(expense("x", 100, LocalDateTime.of(2026, 10, 3, 9, 0)))

        assertTrue(viewModel(store).state.value.budgets.isEmpty())
    }

    @Test
    fun `moving to the previous month reloads the totals`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("oct", 40_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("sep", 12_000, LocalDateTime.of(2026, 9, 3, 9, 0)),
        )
        val vm = viewModel(store)

        vm.showPreviousMonth()

        val state = vm.state.value
        assertEquals(YearMonth.of(2026, 9), state.month)
        assertEquals(Money(12_000), state.total)
    }

    @Test
    fun `the delta is negative when spending fell`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("this", 10_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("last", 25_000, LocalDateTime.of(2026, 9, 3, 9, 0)),
        )

        assertEquals(Money(-15_000), viewModel(store).state.value.delta)
    }
}
