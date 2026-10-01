package com.cointrail.ui.quickadd

import com.cointrail.core.Money
import com.cointrail.domain.model.Category
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class QuickAddViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var expenses: FakeExpenseStore
    private lateinit var categories: FakeCategoryStore
    private lateinit var paymentMethods: FakePaymentMethodStore
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 5, 21, 30)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = now)
    private val transport = Category(id = "preset-transport", name = "Transport", isPreset = true, updatedAt = now)
    private val cash = PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, updatedAt = now)
    private val bkash = PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, updatedAt = now)

    @Before
    fun setUp() {
        expenses = FakeExpenseStore()
        categories = FakeCategoryStore(listOf(food, transport))
        paymentMethods = FakePaymentMethodStore(listOf(cash, bkash))
    }

    private fun viewModel() = QuickAddViewModel(expenses, categories, paymentMethods) { now }

    private fun QuickAddViewModel.enter(text: String) {
        text.forEach { char ->
            if (char == '.') onDecimal() else onDigit(char)
        }
    }

    @Test
    fun `exposes the selectable categories and payment methods`() {
        val vm = viewModel()

        assertEquals(listOf(food, transport), vm.state.value.categories)
        assertEquals(listOf(cash, bkash), vm.state.value.paymentMethods)
    }

    @Test
    fun `hidden categories and payment methods are not offered`() {
        categories = FakeCategoryStore(listOf(food, transport.copy(isHidden = true)))
        paymentMethods = FakePaymentMethodStore(listOf(cash, bkash.copy(isHidden = true)))

        val vm = viewModel()

        assertEquals(listOf(food), vm.state.value.categories)
        assertEquals(listOf(cash), vm.state.value.paymentMethods)
    }

    @Test
    fun `cannot save until a positive amount and a category are chosen`() {
        val vm = viewModel()
        assertFalse(vm.state.value.canSave)

        vm.enter("0")
        assertFalse(vm.state.value.canSave)

        vm.enter("50")
        vm.selectCategory("preset-food")
        assertTrue(vm.state.value.canSave)
    }

    @Test
    fun `keypad edits are reflected in the amount text`() {
        val vm = viewModel()

        vm.enter("12.50")

        assertEquals("12.50", vm.state.value.amountText)
        vm.onBackspace()
        assertEquals("12.5", vm.state.value.amountText)
    }

    @Test
    fun `the amount is shown grouped the way money is displayed`() {
        val vm = viewModel()
        assertEquals("৳0", vm.state.value.amountDisplay)

        vm.enter("1250")

        assertEquals("৳1,250", vm.state.value.amountDisplay)
    }

    @Test
    fun `save records the amount category note payment method and current time`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.enter("12.50")
        vm.selectCategory("preset-food")
        vm.selectPaymentMethod("pm-cash")
        vm.setNote("lunch")

        vm.save()

        assertEquals(1, expenses.calls.size)
        val call = expenses.calls.first()
        assertEquals(Money(1250), call.amount)
        assertEquals("preset-food", call.categoryId)
        assertEquals("lunch", call.note)
        assertEquals("pm-cash", call.paymentMethodId)
        assertEquals(now, call.occurredAt)
        assertTrue(vm.state.value.saved)
    }

    @Test
    fun `note and payment method are optional and a blank note is stored as null`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.enter("5")
        vm.selectCategory("preset-food")
        vm.setNote("   ")

        vm.save()

        assertNull(expenses.calls.first().note)
        assertNull(expenses.calls.first().paymentMethodId)
    }

    @Test
    fun `save does nothing without a category`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.enter("5")

        vm.save()

        assertTrue(expenses.calls.isEmpty())
        assertFalse(vm.state.value.saved)
    }

    @Test
    fun `tapping the selected payment method again clears it`() {
        val vm = viewModel()

        vm.selectPaymentMethod("pm-cash")
        assertEquals("pm-cash", vm.state.value.selectedPaymentMethodId)

        vm.selectPaymentMethod("pm-cash")
        assertNull(vm.state.value.selectedPaymentMethodId)
    }

    @Test
    fun `the note field starts collapsed and can be expanded`() {
        val vm = viewModel()
        assertFalse(vm.state.value.noteExpanded)

        vm.toggleNoteExpanded()
        assertTrue(vm.state.value.noteExpanded)
    }

    @Test
    fun `a successful save resets the form for the next entry`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.enter("12")
        vm.selectCategory("preset-food")
        vm.setNote("snack")

        vm.save()

        assertEquals("", vm.state.value.amountText)
        assertEquals("৳0", vm.state.value.amountDisplay)
        assertNull(vm.state.value.selectedCategoryId)
        assertEquals("", vm.state.value.note)
        assertFalse(vm.state.value.canSave)
    }
}
