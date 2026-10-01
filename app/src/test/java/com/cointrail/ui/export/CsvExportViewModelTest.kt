package com.cointrail.ui.export

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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class CsvExportViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val today: LocalDate = LocalDate.of(2026, 10, 15)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = LocalDateTime.of(2026, 10, 1, 0, 0))
    private val cash = PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, updatedAt = LocalDateTime.of(2026, 10, 1, 0, 0))

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

    private fun viewModel(
        store: FakeExpenseStore,
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food)),
        paymentMethods: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash)),
    ) = CsvExportViewModel(store, categories, paymentMethods) { today }

    @Test
    fun `defaults to the whole current month`() {
        val state = viewModel(FakeExpenseStore()).state.value

        assertEquals(LocalDate.of(2026, 10, 1), state.from)
        assertEquals(today, state.to)
        assertTrue(state.canExport)
        assertEquals("cointrail-2026-10-01_2026-10-15.csv", state.fileName)
    }

    @Test
    fun `an inverted range cannot be exported`() {
        val vm = viewModel(FakeExpenseStore())

        vm.setFrom(LocalDate.of(2026, 10, 20))

        assertFalse(vm.state.value.canExport)
    }

    @Test
    fun `exports live expenses in the inclusive range in chronological order`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense("mid", 200, LocalDateTime.of(2026, 10, 10, 12, 0)),
            expense("first", 100, LocalDateTime.of(2026, 10, 1, 0, 0)),
            expense("late-on-last-day", 300, LocalDateTime.of(2026, 10, 31, 23, 59)),
            expense("next-month", 400, LocalDateTime.of(2026, 11, 1, 0, 0)),
            expense("before", 500, LocalDateTime.of(2026, 9, 30, 23, 59)),
        )
        val vm = viewModel(store)
        vm.setFrom(LocalDate.of(2026, 10, 1))
        vm.setTo(LocalDate.of(2026, 10, 31))

        val csv = vm.buildCsv()

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-01,00:00:00,1.00,Food,,\n" +
                "2026-10-10,12:00:00,2.00,Food,,\n" +
                "2026-10-31,23:59:00,3.00,Food,,\n",
            csv,
        )
    }

    @Test
    fun `deleted expenses are left out`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense("keep", 100, LocalDateTime.of(2026, 10, 5, 12, 0)),
            expense("gone", 200, LocalDateTime.of(2026, 10, 6, 12, 0)),
        )
        store.delete("gone")
        val vm = viewModel(store)

        val csv = vm.buildCsv()

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,12:00:00,1.00,Food,,\n",
            csv,
        )
    }

    @Test
    fun `names resolve for the category and payment method`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeExpenseStore()
        store.seed(
            expense(
                "lunch",
                12_550,
                LocalDateTime.of(2026, 10, 5, 13, 5, 9),
                categoryId = "preset-food",
                paymentMethodId = "pm-cash",
                note = "with the team",
            ),
        )
        val vm = viewModel(store)

        val csv = vm.buildCsv()

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,13:05:09,125.50,Food,Cash,with the team\n",
            csv,
        )
    }

    @Test
    fun `an empty range still yields a header-only file`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel(FakeExpenseStore())

        assertEquals("date,time,amount_taka,category,payment_method,note\n", vm.buildCsv())
    }
}
