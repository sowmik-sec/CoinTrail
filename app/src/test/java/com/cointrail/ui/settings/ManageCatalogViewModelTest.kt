package com.cointrail.ui.settings

import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.FakePaymentMethodStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ManageCatalogViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val pets = Category(id = "custom-pets", name = "Pets", isPreset = false, updatedAt = noon)
    private val cash = PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, updatedAt = noon)
    private val bkash = PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, updatedAt = noon)

    private fun categoriesVm(store: FakeCategoryStore = FakeCategoryStore(listOf(food, pets))) =
        ManageCatalogViewModel(CatalogKind.CATEGORIES, store, FakePaymentMethodStore())

    private fun paymentMethodsVm(store: FakePaymentMethodStore = FakePaymentMethodStore(listOf(cash, bkash))) =
        ManageCatalogViewModel(CatalogKind.PAYMENT_METHODS, FakeCategoryStore(), store)

    @Test
    fun `lists the categories with their preset and hidden flags`() {
        val vm = categoriesVm(FakeCategoryStore(listOf(food, pets.copy(isHidden = true))))

        assertEquals(
            listOf(
                CatalogItemUi(id = "preset-food", name = "Food", isPreset = true, isHidden = false),
                CatalogItemUi(id = "custom-pets", name = "Pets", isPreset = false, isHidden = true),
            ),
            vm.state.value.items,
        )
    }

    @Test
    fun `adding trims the name and clears the input`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCategoryStore(listOf(food))
        val vm = categoriesVm(store)
        vm.setNewName("  Pets  ")
        assertTrue(vm.state.value.canAdd)

        vm.add()

        assertEquals(listOf("Pets"), store.addedNames)
        assertEquals("", vm.state.value.newName)
        assertFalse(vm.state.value.canAdd)
        assertEquals(listOf("Food", "Pets"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `a blank name cannot be added`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCategoryStore(listOf(food))
        val vm = categoriesVm(store)

        assertFalse(vm.state.value.canAdd)
        vm.setNewName("   ")
        assertFalse(vm.state.value.canAdd)
        vm.add()

        assertTrue(store.addedNames.isEmpty())
    }

    @Test
    fun `renaming updates the store and trims the name`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCategoryStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.rename("custom-pets", "  Pet care ")

        assertEquals(listOf("custom-pets" to "Pet care"), store.renames)
        assertEquals(listOf("Food", "Pet care"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `a blank rename is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCategoryStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.rename("custom-pets", "   ")

        assertTrue(store.renames.isEmpty())
    }

    @Test
    fun `hiding and showing toggles through the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCategoryStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.setHidden("preset-food", true)

        assertEquals(listOf("preset-food" to true), store.hiddenChanges)
        assertTrue(vm.state.value.items.first { it.id == "preset-food" }.isHidden)

        vm.setHidden("preset-food", false)

        assertFalse(vm.state.value.items.first { it.id == "preset-food" }.isHidden)
    }

    @Test
    fun `payment methods are managed the same way`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakePaymentMethodStore(listOf(cash, bkash))
        val vm = paymentMethodsVm(store)

        vm.setNewName("Wallet")
        vm.add()
        vm.rename("pm-bkash", "bKash personal")
        vm.setHidden("pm-cash", true)

        assertEquals(listOf("Wallet"), store.addedNames)
        assertEquals(listOf("pm-bkash" to "bKash personal"), store.renames)
        assertEquals(listOf("pm-cash" to true), store.hiddenChanges)
        assertEquals(CatalogKind.PAYMENT_METHODS.title, "Payment methods")
    }
}
