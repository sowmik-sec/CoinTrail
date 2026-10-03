package com.cointrail.ui.settings

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.testing.FakeBudgetStore
import com.cointrail.testing.FakeCategoryStore
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val rent = Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)
    private val shopping = Category(id = "preset-shopping", name = "Shopping", isPreset = true, isHidden = true, updatedAt = noon)

    private fun budget(id: String, categoryId: String?, paisa: Long) =
        Budget(id = id, categoryId = categoryId, monthlyLimit = Money(paisa), updatedAt = noon)

    private fun viewModel(
        budgets: FakeBudgetStore = FakeBudgetStore(),
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food, rent, shopping)),
    ) = BudgetSettingsViewModel(budgets, categories)

    @Test
    fun `the overall row comes first and hidden categories are left out`() {
        val rows = viewModel().state.value.rows

        assertEquals(listOf("Overall", "Food", "Rent"), rows.map { it.label })
        assertNull(rows.first().categoryId)
        assertEquals(listOf("preset-food", "preset-rent"), rows.drop(1).map { it.categoryId })
    }

    @Test
    fun `existing limits are shown per scope`() {
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("overall", null, 2_000_000),
            budget("food-budget", "preset-food", 500_000),
        )

        val rows = viewModel(budgets).state.value.rows

        assertEquals(Money(2_000_000), rows.first { it.categoryId == null }.limit)
        assertEquals(Money(500_000), rows.first { it.categoryId == "preset-food" }.limit)
        assertNull(rows.first { it.categoryId == "preset-rent" }.limit)
    }

    @Test
    fun `setting the overall limit stores it against the null scope`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.setLimit(null, "2000")

        assertEquals(listOf<Pair<String?, Money>>(null to Money(200_000)), budgets.defaultSets)
        assertEquals(Money(200_000), vm.state.value.rows.first().limit)
    }

    @Test
    fun `setting a category limit stores it against that category`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.setLimit("preset-food", "৳1,250.50")

        assertEquals(listOf<Pair<String?, Money>>("preset-food" to Money(125_050)), budgets.defaultSets)
        assertEquals(Money(125_050), vm.state.value.rows.first { it.categoryId == "preset-food" }.limit)
    }

    @Test
    fun `blank or non-positive input is ignored`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.setLimit(null, "")
        vm.setLimit(null, "  ")
        vm.setLimit(null, "abc")
        vm.setLimit(null, "0")

        assertTrue(budgets.defaultSets.isEmpty())
    }

    @Test
    fun `clearing removes the budget for the scope`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)
        vm.setLimit("preset-food", "500")

        vm.clear("preset-food")

        assertEquals(listOf("budget-1"), budgets.clears)
        assertNull(vm.state.value.rows.first { it.categoryId == "preset-food" }.limit)
    }

    @Test
    fun `clearing a scope without a budget is a no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.clear("preset-rent")

        assertTrue(budgets.clears.isEmpty())
    }
}
