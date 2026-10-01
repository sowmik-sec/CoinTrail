package com.cointrail.ui.today

import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.domain.budget.BudgetStatus
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.budget.BudgetAlertLevel
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 5)
    private val month: YearMonth = YearMonth.of(2026, 10)
    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
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
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food)),
        paymentMethods: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash)),
    ) = TodayViewModel(
        expenses = store,
        categories = categories,
        paymentMethods = paymentMethods,
        budgets = budgets,
        budgetAlerts = BudgetAlertTracker(alertStore, notifier) { month },
        today = { today },
    )

    @Test
    fun `lists today's expenses newest first with a running total`() {
        val store = FakeExpenseStore()
        store.seed(
            expense("morning", 10_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("evening", 25_000, LocalDateTime.of(2026, 10, 5, 20, 30)),
            expense("yesterday", 99_999, LocalDateTime.of(2026, 10, 4, 12, 0)),
        )

        val state = viewModel(store).state.value

        assertEquals(listOf("evening", "morning"), state.rows.map { it.id })
        assertEquals(Money(35_000), state.total)
    }

    @Test
    fun `rows carry the category name the payment method name and the time`() {
        val store = FakeExpenseStore()
        store.seed(
            expense(
                "lunch",
                12_550,
                LocalDateTime.of(2026, 10, 5, 13, 5),
                categoryId = "preset-food",
                note = "with the team",
                paymentMethodId = "pm-cash",
            ),
        )

        val row = viewModel(store).state.value.rows.single()

        assertEquals(Money(12_550), row.amount)
        assertEquals("Food", row.categoryName)
        assertEquals("Cash", row.paymentMethodName)
        assertEquals("with the team", row.note)
        assertEquals("13:05", row.time)
    }

    @Test
    fun `an expense without a payment method reports none`() {
        val store = FakeExpenseStore()
        store.seed(expense("x", 100, LocalDateTime.of(2026, 10, 5, 8, 0)))

        assertNull(viewModel(store).state.value.rows.single().paymentMethodName)
    }

    @Test
    fun `an unknown category falls back to its id`() {
        val store = FakeExpenseStore()
        store.seed(expense("x", 100, LocalDateTime.of(2026, 10, 5, 8, 0), categoryId = "custom-1"))

        assertEquals("custom-1", viewModel(store).state.value.rows.single().categoryName)
    }

    @Test
    fun `hidden categories and payment methods are still shown on past expenses`() {
        val store = FakeExpenseStore()
        store.seed(
            expense(
                "x",
                100,
                LocalDateTime.of(2026, 10, 5, 8, 0),
                categoryId = "preset-food",
                paymentMethodId = "pm-cash",
            ),
        )
        val vm = viewModel(
            store,
            categories = FakeCategoryStore(listOf(food.copy(isHidden = true))),
            paymentMethods = FakePaymentMethodStore(listOf(cash.copy(isHidden = true))),
        )

        val row = vm.state.value.rows.single()
        assertEquals("Food", row.categoryName)
        assertEquals("Cash", row.paymentMethodName)
    }

    @Test
    fun `a day with no expenses is empty with a zero total`() {
        val state = viewModel(FakeExpenseStore()).state.value

        assertTrue(state.isEmpty)
        assertEquals(Money.ZERO, state.total)
    }

    @Test
    fun `refresh re-anchors the window to the current day`() {
        var currentDay = LocalDate.of(2026, 10, 5)
        val store = FakeExpenseStore()
        val vm = TodayViewModel(
            expenses = store,
            categories = FakeCategoryStore(listOf(food)),
            paymentMethods = FakePaymentMethodStore(listOf(cash)),
            budgets = FakeBudgetStore(),
            budgetAlerts = BudgetAlertTracker(FakeBudgetAlertStore(), FakeBudgetNotifier()) { month },
            today = { currentDay },
        )
        assertTrue(vm.state.value.isEmpty)

        store.seed(expense("late-night", 5_000, LocalDateTime.of(2026, 10, 6, 0, 30)))
        currentDay = LocalDate.of(2026, 10, 6)

        vm.refresh()

        assertEquals(listOf("late-night"), vm.state.value.rows.map { it.id })
        assertEquals(Money(5_000), vm.state.value.total)
    }

    @Test
    fun `deleting drops the row and its amount and flags it for undo`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense("morning", 10_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("evening", 25_000, LocalDateTime.of(2026, 10, 5, 20, 30)),
        )
        val vm = viewModel(store)

        vm.delete("morning")

        assertEquals(listOf("evening"), vm.state.value.rows.map { it.id })
        assertEquals(Money(25_000), vm.state.value.total)
        assertEquals(listOf("morning"), store.deletes)
        assertEquals("morning", vm.pendingUndoId.value)
    }

    @Test
    fun `undo restores the deleted expense fully and clears the pending undo`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense("morning", 10_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("evening", 25_000, LocalDateTime.of(2026, 10, 5, 20, 30)),
        )
        val vm = viewModel(store)
        vm.delete("morning")

        vm.undoDelete()

        assertEquals(listOf("evening", "morning"), vm.state.value.rows.map { it.id })
        assertEquals(Money(35_000), vm.state.value.total)
        assertEquals(listOf("morning"), store.restores)
        assertNull(vm.pendingUndoId.value)
    }

    @Test
    fun `letting the undo window pass keeps the expense gone without restoring`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense("morning", 10_000, LocalDateTime.of(2026, 10, 5, 9, 0)),
            expense("evening", 25_000, LocalDateTime.of(2026, 10, 5, 20, 30)),
        )
        val vm = viewModel(store)
        vm.delete("morning")

        vm.onUndoDismissed()

        assertEquals(listOf("evening"), vm.state.value.rows.map { it.id })
        assertTrue(store.restores.isEmpty())
        assertNull(vm.pendingUndoId.value)
    }

    @Test
    fun `there are no budget bars when no budgets are set`() {
        val store = FakeExpenseStore()
        store.seed(expense("x", 100, LocalDateTime.of(2026, 10, 5, 8, 0)))

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
        val categories = FakeCategoryStore(listOf(food, Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)))

        val bars = viewModel(store, budgets, categories = categories).state.value.budgets

        assertEquals(listOf("Overall", "Food"), bars.map { it.label })
        assertEquals(Money(80_000), bars[0].spent)
        assertEquals(Money(30_000), bars[1].spent)
    }

    @Test
    fun `a category budget with no spending yet reads zero`() {
        val store = FakeExpenseStore()
        val budgets = FakeBudgetStore()
        budgets.seed(budget("food-budget", "preset-food", 100_000))

        val bar = viewModel(store, budgets).state.value.budgets.single()

        assertEquals(Money.ZERO, bar.spent)
        assertEquals(BudgetStatus.ON_TRACK, bar.status)
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
