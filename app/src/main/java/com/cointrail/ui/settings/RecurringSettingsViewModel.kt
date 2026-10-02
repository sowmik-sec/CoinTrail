package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.data.repo.RecurringStore
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

/** One recurring series as shown in the settings list, with its category/payment names resolved. */
data class RecurringSeriesRow(
    val id: String,
    val amount: Money,
    val categoryId: String,
    val categoryName: String,
    val paymentMethodId: String?,
    val paymentMethodName: String?,
    val note: String?,
    val dayOfMonth: Int,
    val isPaused: Boolean,
)

data class RecurringSettingsUiState(
    val series: List<RecurringSeriesRow> = emptyList(),
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
)

/**
 * Backs the recurring-expenses settings screen (SPEC §6.7): list, create, edit, pause/resume and
 * delete monthly series. A newly created series starts generating from its next occurrence, so it
 * never back-dates an entry for a day that has already passed this month.
 */
class RecurringSettingsViewModel(
    private val recurring: RecurringStore,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {

    val state: StateFlow<RecurringSettingsUiState> = combine(
        recurring.observeAll(),
        categories.observeAll(),
        paymentMethods.observeAll(),
    ) { seriesList, categoryList, paymentMethodList ->
        val categoryNames = categoryList.associate { it.id to it.name }
        val paymentMethodNames = paymentMethodList.associate { it.id to it.name }
        RecurringSettingsUiState(
            series = seriesList.map { it.toRow(categoryNames, paymentMethodNames) },
            categories = categoryList.filterNot { it.isHidden },
            paymentMethods = paymentMethodList.filterNot { it.isHidden },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RecurringSettingsUiState())

    fun create(amountInput: String, categoryId: String, paymentMethodId: String?, note: String, dayOfMonth: Int) {
        val money = parseAmount(amountInput) ?: return
        if (!isValidTarget(categoryId, dayOfMonth)) return
        viewModelScope.launch {
            recurring.add(
                amount = money,
                categoryId = categoryId,
                note = note.ifBlank { null },
                paymentMethodId = paymentMethodId,
                dayOfMonth = dayOfMonth,
                startMonth = firstGenerationMonth(dayOfMonth),
            )
        }
    }

    fun update(id: String, amountInput: String, categoryId: String, paymentMethodId: String?, note: String, dayOfMonth: Int) {
        val money = parseAmount(amountInput) ?: return
        if (!isValidTarget(categoryId, dayOfMonth)) return
        viewModelScope.launch {
            recurring.update(
                id = id,
                amount = money,
                categoryId = categoryId,
                note = note.ifBlank { null },
                paymentMethodId = paymentMethodId,
                dayOfMonth = dayOfMonth,
            )
        }
    }

    fun setPaused(id: String, paused: Boolean) {
        viewModelScope.launch { recurring.setPaused(id, paused) }
    }

    fun delete(id: String) {
        viewModelScope.launch { recurring.delete(id) }
    }

    /**
     * The first month the series may generate for: this month while its day is still ahead, otherwise
     * next month, so a series created after its day has passed does not back-date an entry.
     */
    private fun firstGenerationMonth(dayOfMonth: Int): YearMonth {
        val current = YearMonth.from(today())
        return if (today().dayOfMonth <= dayOfMonth) current else current.plusMonths(1)
    }

    private fun parseAmount(input: String): Money? = Money.fromTaka(input)?.takeIf { it > Money.ZERO }

    private fun isValidTarget(categoryId: String, dayOfMonth: Int): Boolean =
        categoryId.isNotBlank() && dayOfMonth in DAY_RANGE

    private fun RecurringSeries.toRow(
        categoryNames: Map<String, String>,
        paymentMethodNames: Map<String, String>,
    ) = RecurringSeriesRow(
        id = id,
        amount = amount,
        categoryId = categoryId,
        categoryName = categoryNames[categoryId] ?: categoryId,
        paymentMethodId = paymentMethodId,
        paymentMethodName = paymentMethodId?.let { paymentMethodNames[it] },
        note = note,
        dayOfMonth = dayOfMonth,
        isPaused = isPaused,
    )

    private companion object {
        val DAY_RANGE = 1..31
    }
}
