package com.cointrail.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class TodayExpenseRow(
    val id: String,
    val amount: Money,
    val categoryName: String,
    val paymentMethodName: String?,
    val note: String?,
    val time: String,
)

data class TodayUiState(
    val total: Money = Money.ZERO,
    val rows: List<TodayExpenseRow> = emptyList(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val expenses: ExpenseStore,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val day = MutableStateFlow(today())

    val state: StateFlow<TodayUiState> = day.flatMapLatest { date ->
        val dayStart = date.atStartOfDay()
        val dayEnd = dayStart.plusDays(1)
        combine(
            expenses.observeBetween(dayStart, dayEnd),
            expenses.observeTotalBetween(dayStart, dayEnd),
            categories.observeAll(),
            paymentMethods.observeAll(),
        ) { expenseList, total, categoryList, paymentMethodList ->
            val categoryNames = categoryList.associate { it.id to it.name }
            val paymentMethodNames = paymentMethodList.associate { it.id to it.name }
            TodayUiState(
                total = total,
                rows = expenseList.map { expense ->
                    TodayExpenseRow(
                        id = expense.id,
                        amount = expense.amount,
                        categoryName = categoryNames[expense.categoryId] ?: expense.categoryId,
                        paymentMethodName = expense.paymentMethodId?.let { paymentMethodNames[it] },
                        note = expense.note,
                        time = expense.occurredAt.format(TIME_FORMAT),
                    )
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodayUiState())

    /** Re-anchors the screen to the current local day, e.g. when the app returns to the foreground. */
    fun refresh() {
        day.value = today()
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
