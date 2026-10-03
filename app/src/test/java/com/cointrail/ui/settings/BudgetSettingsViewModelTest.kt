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
import java.time.YearMonth

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val noon: LocalDateTime = LocalDateTime.of(2026, 10, 5, 12, 0)
    private val october: YearMonth = YearMonth.of(2026, 10)
    private val november: YearMonth = YearMonth.of(2026, 11)

    private val food = Category(id = "preset-food", name = "Food", isPreset = true, updatedAt = noon)
    private val rent = Category(id = "preset-rent", name = "Rent", isPreset = true, updatedAt = noon)
    private val shopping = Category(id = "preset-shopping", name = "Shopping", isPreset = true, isHidden = true, updatedAt = noon)

    private fun budget(id: String, categoryId: String?, month: YearMonth?, paisa: Long?) = Budget(
        id = id,
        categoryId = categoryId,
        month = month,
        monthlyLimit = paisa?.let(::Money),
        updatedAt = noon,
    )

    private fun viewModel(
        budgets: FakeBudgetStore = FakeBudgetStore(),
        categories: FakeCategoryStore = FakeCategoryStore(listOf(food, rent, shopping)),
        currentMonth: YearMonth = october,
    ) = BudgetSettingsViewModel(budgets, categories) { currentMonth }

    @Test
    fun `the overall row comes first and hidden categories are left out`() {
        val rows = viewModel().state.value.rows

        assertEquals(listOf("Overall", "Food", "Rent"), rows.map { it.label })
        assertNull(rows.first().categoryId)
        assertEquals(listOf("preset-food", "preset-rent"), rows.drop(1).map { it.categoryId })
        assertTrue(rows.all { it.monthState is MonthBudgetState.Inherited })
        assertNull(rows.first { it.categoryId == null }.defaultLimit)
    }

    @Test
    fun `existing default limits are shown per scope`() {
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("overall", null, null, 2_000_000),
            budget("food-budget", "preset-food", null, 500_000),
        )

        val rows = viewModel(budgets).state.value.rows

        assertEquals(Money(2_000_000), rows.first { it.categoryId == null }.defaultLimit)
        assertEquals(Money(500_000), rows.first { it.categoryId == "preset-food" }.defaultLimit)
        assertNull(rows.first { it.categoryId == "preset-rent" }.defaultLimit)
    }

    @Test
    fun `a picked month shows each scope's explicit limit no budget or inherited state`() {
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("overall", null, null, 2_000_000),
            budget("food-budget", "preset-food", null, 500_000),
            budget("eid-food", "preset-food", october, 900_000),
            budget("quiet-rent", "preset-rent", october, null),
        )
        val vm = viewModel(budgets)

        vm.selectMonth(october)

        val rows = vm.state.value.rows.associateBy { it.label }
        assertEquals(BudgetMonthSelection.Month(october), vm.state.value.selection)
        assertEquals(MonthBudgetState.Inherited, rows.getValue("Overall").monthState)
        assertEquals(MonthBudgetState.Override(Money(900_000)), rows.getValue("Food").monthState)
        assertEquals(MonthBudgetState.NoBudget, rows.getValue("Rent").monthState)
        // The scope's default is still visible next to its month state.
        assertEquals(Money(500_000), rows.getValue("Food").defaultLimit)
    }

    @Test
    fun `a month without an override inherits the default with its value`() {
        val budgets = FakeBudgetStore()
        budgets.seed(budget("food-budget", "preset-food", null, 500_000))

        val vm = viewModel(budgets)
        vm.selectMonth(november)

        val foodRow = vm.state.value.rows.first { it.categoryId == "preset-food" }
        assertEquals(MonthBudgetState.Inherited, foodRow.monthState)
        assertEquals(Money(500_000), foodRow.defaultLimit)
    }

    @Test
    fun `setting a month limit creates the override for the picked month only`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        budgets.seed(budget("food-budget", "preset-food", null, 500_000))
        val vm = viewModel(budgets)
        vm.selectMonth(october)

        vm.setMonthLimit("preset-food", "\u09f3900")

        assertEquals(emptyList<Pair<String?, Money>>(), budgets.defaultSets)
        assertEquals(
            listOf(Triple("preset-food" as String?, october, Money(90_000))),
            budgets.overrideSets,
        )
        val foodRow = vm.state.value.rows.first { it.categoryId == "preset-food" }
        assertEquals(Money(90_000), (foodRow.monthState as MonthBudgetState.Override).limit)

        vm.selectMonth(november)
        assertEquals(
            MonthBudgetState.Inherited,
            vm.state.value.rows.first { it.categoryId == "preset-food" }.monthState,
        )
    }

    @Test
    fun `setting an existing month limit again replaces that month's override`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        budgets.seed(budget("eid-food", "preset-food", october, 900_000))
        val vm = viewModel(budgets)
        vm.selectMonth(october)

        vm.setMonthLimit("preset-food", "800")

        assertEquals(1, budgets.overrideSets.size)
        val foodRow = vm.state.value.rows.first { it.categoryId == "preset-food" }
        assertEquals(Money(80_000), (foodRow.monthState as MonthBudgetState.Override).limit)
    }

    @Test
    fun `use default for this month removes the override and nothing else`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        budgets.seed(
            budget("food-budget", "preset-food", null, 500_000),
            budget("eid-food", "preset-food", october, 900_000),
        )
        val vm = viewModel(budgets)
        vm.selectMonth(october)

        vm.useDefaultForMonth("preset-food")

        assertEquals(listOf("preset-food" to october), budgets.overrideRemovals)
        assertTrue(budgets.clears.isEmpty())
        assertEquals(
            MonthBudgetState.Inherited,
            vm.state.value.rows.first { it.categoryId == "preset-food" }.monthState,
        )
    }

    @Test
    fun `no budget for this month is distinct from use default`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)
        vm.selectMonth(october)

        vm.setMonthNoBudget("preset-food")

        assertEquals(listOf("preset-food" to october), budgets.noBudgetSets)
        assertTrue(budgets.overrideRemovals.isEmpty())
        assertEquals(
            MonthBudgetState.NoBudget,
            vm.state.value.rows.first { it.categoryId == "preset-food" }.monthState,
        )
    }

    @Test
    fun `month actions are inert while the default is selected`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.setMonthLimit("preset-food", "900")
        vm.setMonthNoBudget("preset-food")
        vm.useDefaultForMonth("preset-food")

        assertTrue(budgets.overrideSets.isEmpty())
        assertTrue(budgets.noBudgetSets.isEmpty())
        assertTrue(budgets.overrideRemovals.isEmpty())
    }

    @Test
    fun `editing the default budget works as before`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        budgets.seed(budget("food-budget", "preset-food", null, 500_000))
        val vm = viewModel(budgets)

        vm.setDefaultLimit(null, "2000")

        assertEquals(listOf<Pair<String?, Money>>(null to Money(200_000)), budgets.defaultSets)
        assertEquals(Money(200_000), vm.state.value.rows.first().defaultLimit)

        vm.clearDefault("preset-food")

        assertEquals(listOf("food-budget"), budgets.clears)
        assertNull(vm.state.value.rows.first { it.categoryId == "preset-food" }.defaultLimit)
    }

    @Test
    fun `blank or non-positive input is ignored for defaults and month overrides`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)
        vm.selectMonth(october)

        vm.setDefaultLimit(null, "")
        vm.setDefaultLimit(null, "  ")
        vm.setDefaultLimit(null, "abc")
        vm.setDefaultLimit(null, "0")
        vm.setMonthLimit("preset-food", "")
        vm.setMonthLimit("preset-food", "-5")

        assertTrue(budgets.defaultSets.isEmpty())
        assertTrue(budgets.overrideSets.isEmpty())
    }

    @Test
    fun `clearing a scope without a default budget is a no-op`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets)

        vm.clearDefault("preset-rent")

        assertTrue(budgets.clears.isEmpty())
    }

    @Test
    fun `the month stepper walks from the current month in either direction`() {
        val vm = viewModel(currentMonth = october)

        assertEquals(october, vm.stepperMonth.value)

        vm.showPreviousMonth()
        assertEquals(YearMonth.of(2026, 9), vm.stepperMonth.value)
        assertEquals(BudgetMonthSelection.Month(YearMonth.of(2026, 9)), vm.state.value.selection)

        vm.showNextMonth()
        vm.showNextMonth()
        assertEquals(november, vm.stepperMonth.value)

        vm.selectDefault()
        assertEquals(BudgetMonthSelection.Default, vm.state.value.selection)
        assertEquals(october, vm.stepperMonth.value)
    }

    @Test
    fun `a past month can be picked and edited as freely as a future one`() = runTest(mainDispatcherRule.testDispatcher) {
        val budgets = FakeBudgetStore()
        val vm = viewModel(budgets, currentMonth = october)

        vm.selectMonth(YearMonth.of(2026, 3))
        vm.setMonthLimit("preset-food", "400")
        vm.selectMonth(YearMonth.of(2027, 3))
        vm.setMonthNoBudget("preset-food")

        assertEquals(YearMonth.of(2026, 3), budgets.overrideSets.single().second)
        assertEquals(YearMonth.of(2027, 3), budgets.noBudgetSets.single().second)
    }
}
