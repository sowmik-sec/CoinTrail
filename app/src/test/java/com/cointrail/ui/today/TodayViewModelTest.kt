package com.cointrail.ui.today

import com.cointrail.core.Money
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 5)
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

    private fun viewModel(store: FakeExpenseStore) = TodayViewModel(
        expenses = store,
        categories = FakeCategoryStore(listOf(food)),
        paymentMethods = FakePaymentMethodStore(listOf(cash)),
    ) { today }

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
        ) { currentDay }
        assertTrue(vm.state.value.isEmpty)

        store.seed(expense("late-night", 5_000, LocalDateTime.of(2026, 10, 6, 0, 30)))
        currentDay = LocalDate.of(2026, 10, 6)

        vm.refresh()

        assertEquals(listOf("late-night"), vm.state.value.rows.map { it.id })
        assertEquals(Money(5_000), vm.state.value.total)
    }
}
