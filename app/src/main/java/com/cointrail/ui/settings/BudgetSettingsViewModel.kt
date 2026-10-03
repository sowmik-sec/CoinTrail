package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.domain.budget.OVERALL_BUDGET_LABEL
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

/** Which budgets the screen is editing: every scope's default budget, or one specific month. */
sealed interface BudgetMonthSelection {

    /** The scopes' default budgets — the standing monthly limits (SPEC §6.5). */
    data object Default : BudgetMonthSelection

    /** One month, past or future, whose budget overrides are being edited. */
    data class Month(val month: YearMonth) : BudgetMonthSelection
}

/** A scope's budget state for the picked month: which budget actually governs it. */
sealed interface MonthBudgetState {

    /** The month has no override, so the scope's default budget governs. */
    data object Inherited : MonthBudgetState

    /** The month carries an explicit override limit. */
    data class Override(val limit: Money) : MonthBudgetState

    /** The month is explicitly marked as having no budget (no bars, no alerts). */
    data object NoBudget : MonthBudgetState
}

/** One budget scope on the settings screen: the overall budget or a category. */
data class BudgetLimitRow(
    val categoryId: String?,
    val label: String,

    /** The scope's default budget: its row id and limit, either of which is null when unset. */
    val defaultId: String?,
    val defaultLimit: Money?,

    /** The scope's state for the picked month — the default itself when [BudgetMonthSelection.Default]. */
    val monthState: MonthBudgetState,
) {
    val hasDefault: Boolean get() = defaultId != null
}

data class BudgetSettingsUiState(
    val selection: BudgetMonthSelection = BudgetMonthSelection.Default,
    val rows: List<BudgetLimitRow> = emptyList(),
)

/**
 * Backs the budget settings screen — the only place budgets are edited (SPEC §6.5, Q47). A month
 * picker selects the default or any month, past or future; each scope then shows whether the picked
 * month has an explicit limit, an explicit "no budget", or inherits the default. "Use default for
 * this month" and "no budget for this month" are distinct actions, never conflated.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetSettingsViewModel(
    private val budgets: BudgetStore,
    categories: CategoryStore,
    private val currentMonth: () -> YearMonth = { YearMonth.now() },
) : ViewModel() {

    private val selection = MutableStateFlow<BudgetMonthSelection>(BudgetMonthSelection.Default)

    val state: StateFlow<BudgetSettingsUiState> = selection
        .flatMapLatest { picked ->
            combine(budgets.observeAll(), categories.observeAll()) { budgetList, categoryList ->
                BudgetSettingsUiState(selection = picked, rows = rows(budgetList, categoryList, picked))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BudgetSettingsUiState())

    /** The month the stepper is sitting on: the picked month, or the current one under Default. */
    val stepperMonth: StateFlow<YearMonth> = selection
        .map { picked -> (picked as? BudgetMonthSelection.Month)?.month ?: currentMonth() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, currentMonth())

    fun selectDefault() {
        selection.value = BudgetMonthSelection.Default
    }

    fun selectMonth(month: YearMonth) {
        selection.value = BudgetMonthSelection.Month(month)
    }

    fun showPreviousMonth() = selectMonth(stepperMonth.value.minusMonths(1))

    fun showNextMonth() = selectMonth(stepperMonth.value.plusMonths(1))

    /** Parses and saves a scope's default limit; blank or non-positive input is ignored. */
    fun setDefaultLimit(categoryId: String?, input: String) {
        val money = input.toPositiveMoney() ?: return
        viewModelScope.launch { budgets.setDefault(categoryId, money) }
    }

    /** Removes a scope's default budget; a scope without one is a no-op. */
    fun clearDefault(categoryId: String?) {
        val defaultId = state.value.rows.firstOrNull { it.categoryId == categoryId }?.defaultId ?: return
        viewModelScope.launch { budgets.clear(defaultId) }
    }

    /** Sets or updates the picked month's override limit for a scope, touching no other month. */
    fun setMonthLimit(categoryId: String?, input: String) {
        val month = pickedMonth() ?: return
        val money = input.toPositiveMoney() ?: return
        viewModelScope.launch { budgets.setOverride(categoryId, month, money) }
    }

    /** Marks the picked month as explicitly "no budget" for a scope. */
    fun setMonthNoBudget(categoryId: String?) {
        val month = pickedMonth() ?: return
        viewModelScope.launch { budgets.setNoBudget(categoryId, month) }
    }

    /** Removes the picked month's override, handing the month back to the default; a no-op without one. */
    fun useDefaultForMonth(categoryId: String?) {
        val month = pickedMonth() ?: return
        viewModelScope.launch { budgets.removeOverride(categoryId, month) }
    }

    private fun pickedMonth(): YearMonth? =
        (state.value.selection as? BudgetMonthSelection.Month)?.month

    private fun rows(budgetList: List<Budget>, categoryList: List<Category>, picked: BudgetMonthSelection): List<BudgetLimitRow> =
        buildList {
            add(budgetRow(categoryId = null, label = OVERALL_BUDGET_LABEL, budgetList = budgetList, picked = picked))
            categoryList.filterNot { it.isHidden }.forEach { category ->
                add(
                    budgetRow(
                        categoryId = category.id,
                        label = category.name,
                        budgetList = budgetList,
                        picked = picked,
                    ),
                )
            }
        }

    private fun budgetRow(
        categoryId: String?,
        label: String,
        budgetList: List<Budget>,
        picked: BudgetMonthSelection,
    ): BudgetLimitRow {
        val scopeRows = budgetList.filter { it.scopeKey == scopeKey(categoryId) && !it.isDeleted }
        val default = scopeRows.firstOrNull { it.isDefault }
        val monthState = when (picked) {
            is BudgetMonthSelection.Default -> MonthBudgetState.Inherited
            is BudgetMonthSelection.Month -> {
                // Deliberately not EffectiveBudgets.forMonth: editing needs the full tri-state —
                // including which months inherit and which are explicitly "no budget" — where the
                // seam resolves only the limit-bearing budgets that govern display.
                val override = scopeRows.firstOrNull { it.month == picked.month }
                when {
                    override == null -> MonthBudgetState.Inherited
                    override.isNoBudget -> MonthBudgetState.NoBudget
                    else -> MonthBudgetState.Override(
                        limit = requireNotNull(override.monthlyLimit) { "A live override must carry its limit" },
                    )
                }
            }
        }
        return BudgetLimitRow(
            categoryId = categoryId,
            label = label,
            defaultId = default?.id,
            defaultLimit = default?.monthlyLimit,
            monthState = monthState,
        )
    }

    private fun scopeKey(categoryId: String?): String = categoryId ?: Budget.OVERALL_SCOPE

    private fun String.toPositiveMoney(): Money? =
        Money.fromTaka(this)?.takeIf { it > Money.ZERO }
}
