package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.domain.budget.OVERALL_BUDGET_LABEL
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One budget scope on the settings screen: the overall budget or a category. */
data class BudgetLimitRow(
    val categoryId: String?,
    val budgetId: String?,
    val label: String,
    val limit: Money?,
) {
    val hasLimit: Boolean get() = limit != null
}

data class BudgetSettingsUiState(
    val rows: List<BudgetLimitRow> = emptyList(),
)

/**
 * Backs the budget settings screen. The overall budget is always the first row, followed by the
 * visible categories. Setting a limit upserts by scope; clearing tombstones it. Limits are monthly
 * with no rollover (SPEC §6.5).
 */
class BudgetSettingsViewModel(
    private val budgets: BudgetStore,
    private val categories: CategoryStore,
) : ViewModel() {

    val state: StateFlow<BudgetSettingsUiState> = combine(
        budgets.observeAll(),
        categories.observeAll(),
    ) { budgetList, categoryList ->
        val rows = buildList {
            val overall = budgetList.firstOrNull { it.categoryId == null }
            add(BudgetLimitRow(categoryId = null, budgetId = overall?.id, label = OVERALL_BUDGET_LABEL, limit = overall?.monthlyLimit))
            categoryList.filterNot { it.isHidden }.forEach { category ->
                val budget = budgetList.firstOrNull { it.categoryId == category.id }
                add(
                    BudgetLimitRow(
                        categoryId = category.id,
                        budgetId = budget?.id,
                        label = category.name,
                        limit = budget?.monthlyLimit,
                    ),
                )
            }
        }
        BudgetSettingsUiState(rows = rows)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, BudgetSettingsUiState())

    /** Parses and saves a default limit for a scope; blank or non-positive input is ignored. */
    fun setLimit(categoryId: String?, input: String) {
        val money = Money.fromTaka(input) ?: return
        if (money <= Money.ZERO) return
        viewModelScope.launch { budgets.setDefault(categoryId, money) }
    }

    /** Removes the budget for a scope; a scope without one is a no-op. */
    fun clear(categoryId: String?) {
        val budgetId = state.value.rows.firstOrNull { it.categoryId == categoryId }?.budgetId ?: return
        viewModelScope.launch { budgets.clear(budgetId) }
    }
}
