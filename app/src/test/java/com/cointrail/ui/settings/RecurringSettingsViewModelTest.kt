package com.cointrail.ui.settings

import com.cointrail.core.Money
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.FakePaymentMethodStore
import com.cointrail.testing.FakeRecurringStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class RecurringSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 20, 12, 0)
    private val today: LocalDate = LocalDate.of(2026, 10, 20)

    private val rent = Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)
    private val utilities = Category(id = "preset-utilities", name = "Utilities", isPreset = true, updatedAt = noon)
    private val bkash = PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, updatedAt = noon)

    private val store = FakeRecurringStore()
    private val categories = FakeCategoryStore(listOf(rent, utilities))
    private val paymentMethods = FakePaymentMethodStore(listOf(bkash))

    private fun viewModel() = RecurringSettingsViewModel(store, categories, paymentMethods) { today }

    private fun series(id: String, dayOfMonth: Int = 5, isPaused: Boolean = false) = RecurringSeries(
        id = id,
        amount = Money(150_000),
        categoryId = "preset-rent",
        note = "internet",
        paymentMethodId = "pm-bkash",
        dayOfMonth = dayOfMonth,
        startMonth = YearMonth.of(2026, 10),
        lastGeneratedMonth = null,
        isPaused = isPaused,
        updatedAt = noon,
    )

    @Test
    fun `lists series with resolved category and payment names`() {
        store.seed(series("r1"))

        val row = viewModel().state.value.series.single()

        assertEquals("Rent", row.categoryName)
        assertEquals("bKash", row.paymentMethodName)
        assertEquals(5, row.dayOfMonth)
        assertEquals(Money(150_000), row.amount)
    }

    @Test
    fun `creating on a day still ahead starts this month`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().create("1500", "preset-rent", "pm-bkash", "internet", 25)

        val call = store.creates.single()
        assertEquals(Money(150_000), call.amount)
        assertEquals("preset-rent", call.categoryId)
        assertEquals(25, call.dayOfMonth)
        assertEquals(YearMonth.of(2026, 10), call.startMonth)
    }

    @Test
    fun `creating after the day passed starts next month`() = runTest(mainDispatcherRule.testDispatcher) {
        viewModel().create("1500", "preset-rent", null, "", 5)

        assertEquals(YearMonth.of(2026, 11), store.creates.single().startMonth)
        assertEquals(null, store.creates.single().note)
    }

    @Test
    fun `invalid input never creates a series`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()

        vm.create("0", "preset-rent", null, "", 5)
        vm.create("1500", "preset-rent", null, "", 40)
        vm.create("1500", "", null, "", 5)

        assertTrue(store.creates.isEmpty())
    }

    @Test
    fun `editing updates the series in place`() = runTest(mainDispatcherRule.testDispatcher) {
        store.seed(series("r1"))
        val vm = viewModel()

        vm.update("r1", "800", "preset-utilities", "pm-bkash", "wifi", 3)

        val call = store.updates.single()
        assertEquals("r1", call.id)
        assertEquals(Money(80_000), call.amount)
        assertEquals("preset-utilities", call.categoryId)
        assertEquals(3, call.dayOfMonth)
        assertEquals("wifi", call.note)
        assertEquals(Money(80_000), vm.state.value.series.single().amount)
    }

    @Test
    fun `pausing and deleting go through the store`() = runTest(mainDispatcherRule.testDispatcher) {
        store.seed(series("r1"))
        val vm = viewModel()

        vm.setPaused("r1", true)
        vm.delete("r1")

        assertEquals(listOf("r1" to true), store.pauseChanges)
        assertEquals(listOf("r1"), store.deletes)
        assertTrue(vm.state.value.series.isEmpty())
    }
}
