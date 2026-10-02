package com.cointrail.ui.home

import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.domain.budget.BudgetAlertLevel
import com.cointrail.domain.budget.BudgetStatus
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.testing.FakeBudgetAlertStore
import com.cointrail.testing.FakeBudgetNotifier
import com.cointrail.testing.FakeBudgetStore
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.FakeExpenseStore
import com.cointrail.testing.FakePaymentMethodStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 5)
    private val month: YearMonth = YearMonth.of(2026, 10)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val rent = Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)
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
        alertStore: FakeBudgetAlertStore = FakeBudgetAlertStore(),
        notifier: FakeBudgetNotifier = FakeBudgetNotifier(),
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food, rent)),
        paymentMethods: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash)),
    ) = HomeViewModel(
        expenses = store,
        categories = categories,
        paymentMethods = paymentMethods,
        budgets = budgets,
        budgetAlerts = BudgetAlertTracker(alertStore, notifier) { month },
        today = { today },
    )

    @Test
    fun `the hero carries the month total and the move against the previous month`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("oct", 115_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("sep", 100_000, LocalDateTime.of(2026, 9, 3, 9, 0)),
            expense("aug", 999_999, LocalDateTime.of(2026, 8, 3, 9, 0)),
        )

        val state = viewModel(store).state.value

        assertEquals(Money(115_000), state.monthTotal)
        assertEquals(Money(15_000), state.momDelta)
        assertEquals(15, state.momPercent)
    }

    @Test
    fun `without previous-month data the comparison stays hidden`() {
        val store = FakeExpenseStore()
        store.seed(expense("oct", 50_000, LocalDateTime.of(2026, 10, 3, 9, 0)))

        val state = viewModel(store).state.value

        assertEquals(Money(50_000), state.monthTotal)
        assertNull(state.momDelta)
        assertNull(state.momPercent)
    }

    @Test
    fun `the today block carries today's total and count`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("today", 35_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("also-today", 5_000, LocalDateTime.of(2026, 10, 5, 20, 0)),
            expense("other-day", 99_999, LocalDateTime.of(2026, 10, 4, 9, 0)),
        )

        val state = viewModel(store).state.value

        assertEquals(Money(40_000), state.todayTotal)
        assertEquals(2, state.todayCount)
    }

    @Test
    fun `recent lists today's entries newest first and caps at five`() {
        val store = FakeExpenseStore()
        (1..6).forEach { n ->
            store.seed(expense("e$n", n * 100L, LocalDateTime.of(2026, 10, 5, 8, n)))
        }
        store.seed(expense("yesterday", 99_999, LocalDateTime.of(2026, 10, 4, 9, 0)))

        val state = viewModel(store).state.value

        assertEquals(6, state.todayCount)
        assertEquals(listOf("e6", "e5", "e4", "e3", "e2"), state.recent.map { it.id })
    }

    @Test
    fun `an empty today reads zero while history still counts`() {
        val store = FakeExpenseStore()
        store.seed(expense("yesterday", 99_999, LocalDateTime.of(2026, 10, 4, 9, 0)))

        val state = viewModel(store).state.value

        assertEquals(Money.ZERO, state.todayTotal)
        assertEquals(0, state.todayCount)
        assertTrue(state.recent.isEmpty())
        assertTrue(state.hasAnyExpenses)
    }

    @Test
    fun `a first run with no expenses at all flags the call to action`() {
        val state = viewModel(FakeExpenseStore()).state.value

        assertFalse(state.hasAnyExpenses)
        assertEquals(Money.ZERO, state.monthTotal)
    }

    @Test
    fun `row times use the given clock format so the device's 12 or 24 hour setting wins`() {
        val store = FakeExpenseStore()
        store.seed(expense("lunch", 100, LocalDateTime.of(2026, 10, 5, 13, 5), paymentMethodId = "pm-cash"))

        val row = HomeViewModel(
            expenses = store,
            categories = FakeCategoryStore(listOf(food)),
            paymentMethods = FakePaymentMethodStore(listOf(cash)),
            budgets = FakeBudgetStore(),
            budgetAlerts = BudgetAlertTracker(FakeBudgetAlertStore(), FakeBudgetNotifier()) { month },
            today = { today },
            timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
        ).state.value.recent.single()

        assertEquals("1:05 PM", row.time)
        assertEquals("Food", row.categoryName)
        assertEquals("Cash", row.paymentMethodName)
    }

    @Test
    fun `refresh re-anchors the window to the current day`() {
        var currentDay = LocalDate.of(2026, 10, 5)
        val store = FakeExpenseStore()
        val vm = HomeViewModel(
            expenses = store,
            categories = FakeCategoryStore(listOf(food)),
            paymentMethods = FakePaymentMethodStore(listOf(cash)),
            budgets = FakeBudgetStore(),
            budgetAlerts = BudgetAlertTracker(FakeBudgetAlertStore(), FakeBudgetNotifier()) { month },
            today = { currentDay },
        )
        assertEquals(0, vm.state.value.todayCount)

        store.seed(expense("late-night", 5_000, LocalDateTime.of(2026, 10, 6, 0, 30)))
        currentDay = LocalDate.of(2026, 10, 6)

        vm.refresh()

        assertEquals(Money(5_000), vm.state.value.todayTotal)
        assertEquals(listOf("late-night"), vm.state.value.recent.map { it.id })
    }

    @Test
    fun `there are no budget bars when no budgets are set`() {
        val store = FakeExpenseStore()
        store.seed(expense("x", 100, LocalDateTime.of(2026, 10, 3, 8, 0)))

        assertTrue(viewModel(store).state.value.budgets.isEmpty())
    }

    @Test
    fun `the overall budget is measured against the whole month`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("in-month", 40_000, LocalDateTime.of(2026, 10, 3, 9, 0)),
            expense("today", 5_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("last-month", 999_999, LocalDateTime.of(2026, 9, 30, 9, 0)),
        )
        val budgets = FakeBudgetStore()
        budgets.seed(budget("overall", null, 100_000))

        val bar = viewModel(store, budgets).state.value.budgets.single()

        assertEquals("Overall", bar.label)
        assertEquals(Money(45_000), bar.spent)
        assertEquals(Money(100_000), bar.limit)
        assertEquals(BudgetStatus.ON_TRACK, bar.status)
    }

    @Test
    fun `category budgets are measured against that category's month and come after the overall bar`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("food", 30_000, LocalDateTime.of(2026, 10, 3, 9, 0), categoryId = "preset-food"),
            expense("rent", 50_000, LocalDateTime.of(2026, 10, 4, 9, 0), categoryId = "preset-rent"),
        )
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("food-budget", "preset-food", 100_000),
            budget("overall", null, 200_000),
        )

        val bars = viewModel(store, budgets).state.value.budgets

        assertEquals(listOf("Overall", "Food"), bars.map { it.label })
        assertEquals(Money(80_000), bars[0].spent)
        assertEquals(Money(30_000), bars[1].spent)
    }

    @Test
    fun `an expense that pushes the month past eighty percent raises one warning`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense("big", 8_500, LocalDateTime.of(2026, 10, 3, 9, 0)))
        val budgets = FakeBudgetStore()
        budgets.seed(budget("overall", null, 10_000))
        val notifier = FakeBudgetNotifier()

        viewModel(store, budgets, notifier = notifier)
        advanceUntilIdle()

        assertEquals(listOf(BudgetAlertLevel.WARNING), notifier.alerts.map { it.level })
    }

    @Test
    fun `spending on raises the warning once and then the exceeded alert`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense("first", 8_500, LocalDateTime.of(2026, 10, 3, 9, 0)))
        val budgets = FakeBudgetStore()
        budgets.seed(budget("overall", null, 10_000))
        val notifier = FakeBudgetNotifier()
        viewModel(store, budgets, notifier = notifier)
        advanceUntilIdle()

        store.seed(expense("more", 2_000, LocalDateTime.of(2026, 10, 4, 9, 0)))
        advanceUntilIdle()

        assertEquals(
            listOf(BudgetAlertLevel.WARNING, BudgetAlertLevel.EXCEEDED),
            notifier.alerts.map { it.level },
        )
    }
}
