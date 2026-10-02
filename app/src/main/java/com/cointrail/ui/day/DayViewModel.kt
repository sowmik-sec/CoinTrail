package com.cointrail.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.data.repo.BudgetStore
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.domain.budget.BudgetProgressCalculator
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.CategoryTotal
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.ui.components.ExpenseRowUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DayUiState(
    val total: Money = Money.ZERO,
    val rows: List<ExpenseRowUi> = emptyList(),
    val budgets: List<BudgetProgress> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * Backs the Day screen (SPEC §6.1): one date's expenses newest first with that day's total. The
 * [date] parameter is fixed — today is simply the Day screen for the current date.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayViewModel(
    private val date: LocalDate,
    private val expenses: ExpenseStore,
    categories: CategoryStore,
    paymentMethods: PaymentMethodStore,
    budgets: BudgetStore,
    budgetAlerts: BudgetAlertTracker,
    timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
) : ViewModel() {

    private val undoId = MutableStateFlow<String?>(null)

    /** The expense awaiting an undo decision, or null when no delete is pending. */
    val pendingUndoId: StateFlow<String?> = undoId.asStateFlow()

    val state: StateFlow<DayUiState> = run {
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        val monthStart = date.withDayOfMonth(1).atStartOfDay()
        val monthEnd = monthStart.plusMonths(1)
        combine(
            expenses.observeBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(monthStart, monthEnd),
            expenses.observeCategoryTotals(monthStart, monthEnd),
        ) { expenseList, dayTotal, monthTotal, categoryTotals ->
            Snapshot(expenseList, dayTotal, monthTotal, categoryTotals)
        }
            .combine(categories.observeAll()) { snapshot, categoryList -> snapshot to categoryList }
            .combine(paymentMethods.observeAll()) { (snapshot, categoryList), paymentMethodList ->
                SnapshotWithCatalogs(snapshot, categoryList, paymentMethodList)
            }
            .combine(budgets.observeAll()) { (snapshot, categoryList, paymentMethodList), budgetList ->
                val categoryNames = categoryList.associate { it.id to it.name }
                val paymentMethodNames = paymentMethodList.associate { it.id to it.name }
                DayUiState(
                    total = snapshot.dayTotal,
                    rows = snapshot.expenses.map { expense ->
                        ExpenseRowUi(
                            id = expense.id,
                            amount = expense.amount,
                            categoryName = categoryNames[expense.categoryId] ?: expense.categoryId,
                            paymentMethodName = expense.paymentMethodId?.let { paymentMethodNames[it] },
                            note = expense.note,
                            time = expense.occurredAt.format(timeFormat),
                        )
                    },
                    budgets = BudgetProgressCalculator.forMonth(
                        budgets = budgetList,
                        categories = categoryList,
                        overallSpent = snapshot.monthTotal,
                        spentByCategory = snapshot.categoryTotals.associate { it.categoryId to it.total },
                        categoryNames = categoryNames,
                    ),
                )
            }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DayUiState())

    init {
        // Every change to month-to-date spending is a chance to cross a budget threshold.
        viewModelScope.launch {
            state.collect { ui -> budgetAlerts.evaluate(ui.budgets) }
        }
    }

    /** Tombstones the expense and offers an undo window. */
    fun delete(id: String) {
        viewModelScope.launch {
            expenses.delete(id)
            undoId.value = id
        }
    }

    /** Brings back the expense most recently deleted. */
    fun undoDelete() {
        val id = undoId.value ?: return
        undoId.value = null
        viewModelScope.launch { expenses.restore(id) }
    }

    /** Called when the undo snackbar is dismissed without action; the deletion stands. */
    fun onUndoDismissed() {
        undoId.value = null
    }

    private data class Snapshot(
        val expenses: List<Expense>,
        val dayTotal: Money,
        val monthTotal: Money,
        val categoryTotals: List<CategoryTotal>,
    )

    private data class SnapshotWithCatalogs(
        val snapshot: Snapshot,
        val categories: List<Category>,
        val paymentMethods: List<PaymentMethod>,
    )
}
