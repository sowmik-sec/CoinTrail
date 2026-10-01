package com.cointrail.ui.export

import androidx.lifecycle.ViewModel
import com.cointrail.core.ExpenseCsv
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.time.LocalDate

data class CsvExportUiState(
    val from: LocalDate,
    val to: LocalDate,
) {
    /** Both endpoints are inclusive, so the range is only valid when it does not run backwards. */
    val canExport: Boolean get() = !from.isAfter(to)

    val fileName: String get() = "cointrail-${from}_${to}.csv"
}

/**
 * Backs the CSV export screen: the user picks a date range (defaulting to the current month) and
 * gets a snapshot of every live expense in it. [buildCsv] reads once, renders synchronously and
 * never mutates anything, so writing the result wherever the user chooses is the caller's job.
 */
class CsvExportViewModel(
    private val expenses: ExpenseStore,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
    today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    private val _state = MutableStateFlow(
        CsvExportUiState(from = today().withDayOfMonth(1), to = today()),
    )
    val state: StateFlow<CsvExportUiState> = _state.asStateFlow()

    fun setFrom(date: LocalDate) {
        _state.update { it.copy(from = date) }
    }

    fun setTo(date: LocalDate) {
        _state.update { it.copy(to = date) }
    }

    suspend fun buildCsv(): String {
        val current = _state.value
        val from = current.from.atStartOfDay()
        val to = current.to.plusDays(1).atStartOfDay()
        val expensesInRange = expenses.loadBetween(from, to).sortedBy { it.occurredAt }
        val categoryNames = categories.observeAll().first().associate { it.id to it.name }
        val paymentMethodNames = paymentMethods.observeAll().first().associate { it.id to it.name }
        return ExpenseCsv.render(expensesInRange, categoryNames, paymentMethodNames)
    }
}
