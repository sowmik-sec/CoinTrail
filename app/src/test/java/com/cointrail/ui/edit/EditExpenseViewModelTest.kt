package com.cointrail.ui.edit

import com.cointrail.core.Money
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.FakeExpenseStore
import com.cointrail.testing.FakePaymentMethodStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class EditExpenseViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)
    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val transport = Category(id = "preset-transport", name = "Transport", isPreset = true, updatedAt = noon)
    private val cash = PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, updatedAt = noon)
    private val bkash = PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, updatedAt = noon)

    private fun expense(
        id: String = "lunch",
        paisa: Long = 1_250,
        at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 13, 5),
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

    private fun viewModel(
        id: String = "lunch",
        store: FakeExpenseStore = FakeExpenseStore(),
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food, transport)),
        paymentMethods: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash, bkash)),
    ) = EditExpenseViewModel(id, store, categories, paymentMethods)

    private fun EditExpenseViewModel.enter(text: String) {
        text.forEach { char ->
            if (char == '.') onDecimal() else onDigit(char)
        }
    }

    @Test
    fun `loads the expense and pre-fills every field`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense(note = "with the team", paymentMethodId = "pm-cash"))

        val state = viewModel(store = store).state.value

        assertFalse(state.loading)
        assertEquals("12.50", state.amountText)
        assertEquals("৳12.50", state.amountDisplay)
        assertEquals("preset-food", state.selectedCategoryId)
        assertEquals("pm-cash", state.selectedPaymentMethodId)
        assertEquals("with the team", state.note)
        assertTrue(state.noteExpanded)
        assertEquals(LocalDateTime.of(2026, 10, 5, 13, 5), state.occurredAt)
        assertTrue(state.canSave)
    }

    @Test
    fun `offers the selectable categories and payment methods and hides none of the presets`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense())

        val state = viewModel(store = store).state.value

        assertEquals(listOf(food, transport), state.categories)
        assertEquals(listOf(cash, bkash), state.paymentMethods)
    }

    @Test
    fun `hidden categories and payment methods are not offered`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense())

        val state = viewModel(
            store = store,
            categories = FakeCategoryStore(listOf(food, transport.copy(isHidden = true))),
            paymentMethods = FakePaymentMethodStore(listOf(cash, bkash.copy(isHidden = true))),
        ).state.value

        assertEquals(listOf(food), state.categories)
        assertEquals(listOf(cash), state.paymentMethods)
    }

    @Test
    fun `cannot save once the amount is cleared`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense())
        val vm = viewModel(store = store)
        assertTrue(vm.state.value.canSave)

        repeat(5) { vm.onBackspace() }

        assertFalse(vm.state.value.canSave)
        vm.onDigit('5')
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `saving writes the edited amount category note payment method and datetime`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        val original = expense(note = "old", paymentMethodId = "pm-cash")
        store.seed(original)
        val vm = viewModel(store = store)
        repeat(5) { vm.onBackspace() }

        vm.enter("25.75")
        vm.selectCategory("preset-transport")
        vm.setNote("new note")
        vm.selectPaymentMethod("pm-bkash")
        vm.onDateSelected(LocalDate.of(2026, 10, 2))
        vm.onTimeSelected(LocalTime.of(9, 30))

        vm.save()

        val written = store.updates.single()
        assertEquals("lunch", written.id)
        assertEquals(Money(2_575), written.amount)
        assertEquals("preset-transport", written.categoryId)
        assertEquals("new note", written.note)
        assertEquals("pm-bkash", written.paymentMethodId)
        assertEquals(LocalDateTime.of(2026, 10, 2, 9, 30), written.occurredAt)
        assertEquals(original.createdAt, written.createdAt)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `an unknown expense is reported as not found`() = runTest(mainDispatcherRule.testDispatcher) {
        val state = viewModel(id = "missing").state.value

        assertFalse(state.loading)
        assertTrue(state.notFound)
        assertNull(state.occurredAt)
    }

    @Test
    fun `tapping the selected payment method again clears it and a blank note is stored as null`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(expense(note = "old", paymentMethodId = "pm-cash"))
        val vm = viewModel(store = store)

        vm.selectPaymentMethod("pm-cash")
        vm.setNote("   ")
        vm.save()

        val written = store.updates.single()
        assertNull(written.paymentMethodId)
        assertNull(written.note)
    }
}
