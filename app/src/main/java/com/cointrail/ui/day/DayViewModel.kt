package com.cointrail.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.ui.components.ExpenseRowUi
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
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * Backs the Day screen (SPEC §6.1): one date's expenses newest first with that day's total. The
 * [date] parameter is fixed — today is simply the Day screen for the current date.
 */
class DayViewModel(
    private val date: LocalDate,
    private val expenses: ExpenseStore,
    categories: CategoryStore,
    paymentMethods: PaymentMethodStore,
    timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm"),
) : ViewModel() {

    private val undoId = MutableStateFlow<String?>(null)

    /** The expense awaiting an undo decision, or null when no delete is pending. */
    val pendingUndoId: StateFlow<String?> = undoId.asStateFlow()

    val state: StateFlow<DayUiState> = run {
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        combine(
            expenses.observeBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(dayStart, dayEnd),
            categories.observeAll(),
            paymentMethods.observeAll(),
        ) { dayRows, dayTotal, categoryList, paymentMethodList ->
            val categoryNames = categoryList.associate { it.id to it.name }
            val paymentMethodNames = paymentMethodList.associate { it.id to it.name }
            DayUiState(
                total = dayTotal,
                rows = dayRows.map { expense ->
                    ExpenseRowUi(
                        id = expense.id,
                        amount = expense.amount,
                        categoryName = categoryNames[expense.categoryId] ?: expense.categoryId,
                        paymentMethodName = expense.paymentMethodId?.let { paymentMethodNames[it] },
                        note = expense.note,
                        time = expense.occurredAt.format(timeFormat),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DayUiState())

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
}
