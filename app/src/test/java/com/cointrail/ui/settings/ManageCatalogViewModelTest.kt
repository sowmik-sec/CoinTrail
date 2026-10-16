package com.cointrail.ui.settings

import com.cointrail.data.repo.CatalogKind
import com.cointrail.domain.model.CatalogItem
import com.cointrail.testing.FakeCatalogStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageCatalogViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val food = CatalogItem(id = "preset-food", name = "Food", isPreset = true)
    private val pets = CatalogItem(id = "custom-pets", name = "Pets", isPreset = false)
    private val cash = CatalogItem(id = "pm-cash", name = "Cash", isPreset = true)
    private val bkash = CatalogItem(id = "pm-bkash", name = "bKash", isPreset = true)

    private fun categoriesVm(store: FakeCatalogStore = FakeCatalogStore(listOf(food, pets))) =
        ManageCatalogViewModel(CatalogKind.CATEGORIES, store)

    private fun paymentMethodsVm(store: FakeCatalogStore = FakeCatalogStore(initialPaymentMethods = listOf(cash, bkash))) =
        ManageCatalogViewModel(CatalogKind.PAYMENT_METHODS, store)

    @Test
    fun `lists the categories with their preset and hidden flags`() {
        val vm = categoriesVm(FakeCatalogStore(listOf(food, pets.copy(isHidden = true))))

        assertEquals(
            listOf(food, pets.copy(isHidden = true)),
            vm.state.value.items,
        )
    }

    @Test
    fun `adding trims the name and clears the input`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(listOf(food))
        val vm = categoriesVm(store)
        vm.setNewName("  Pets  ")
        assertTrue(vm.state.value.canAdd)

        vm.add()

        assertEquals(
            listOf(CatalogKind.CATEGORIES to "Pets"),
            store.addedNames,
        )
        assertEquals("", vm.state.value.newName)
        assertFalse(vm.state.value.canAdd)
        assertEquals(listOf("Food", "Pets"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `a blank name cannot be added`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(listOf(food))
        val vm = categoriesVm(store)

        assertFalse(vm.state.value.canAdd)
        vm.setNewName("   ")
        assertFalse(vm.state.value.canAdd)
        vm.add()

        assertTrue(store.addedNames.isEmpty())
    }

    @Test
    fun `renaming updates the store and trims the name`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.rename("custom-pets", "  Pet care ")

        assertEquals(
            listOf(Triple(CatalogKind.CATEGORIES, "custom-pets", "Pet care")),
            store.renames,
        )
        assertEquals(listOf("Food", "Pet care"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `a blank rename is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.rename("custom-pets", "   ")

        assertTrue(store.renames.isEmpty())
    }

    @Test
    fun `hiding and showing toggles through the store`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(listOf(food, pets))
        val vm = categoriesVm(store)

        vm.setHidden("preset-food", true)

        assertEquals(
            listOf(Triple(CatalogKind.CATEGORIES, "preset-food", true)),
            store.hiddenChanges,
        )
        assertTrue(vm.state.value.items.first { it.id == "preset-food" }.isHidden)

        vm.setHidden("preset-food", false)

        assertFalse(vm.state.value.items.first { it.id == "preset-food" }.isHidden)
    }

    @Test
    fun `payment methods are managed the same way`() = runTest(mainDispatcherRule.testDispatcher) {
        val store = FakeCatalogStore(initialPaymentMethods = listOf(cash, bkash))
        val vm = paymentMethodsVm(store)

        vm.setNewName("Wallet")
        vm.add()
        vm.rename("pm-bkash", "bKash personal")
        vm.setHidden("pm-cash", true)

        assertEquals(listOf(CatalogKind.PAYMENT_METHODS to "Wallet"), store.addedNames)
        assertEquals(
            listOf(Triple(CatalogKind.PAYMENT_METHODS, "pm-bkash", "bKash personal")),
            store.renames,
        )
        assertEquals(
            listOf(Triple(CatalogKind.PAYMENT_METHODS, "pm-cash", true)),
            store.hiddenChanges,
        )
        assertEquals(CatalogKind.PAYMENT_METHODS.title, "Payment methods")
    }
}
