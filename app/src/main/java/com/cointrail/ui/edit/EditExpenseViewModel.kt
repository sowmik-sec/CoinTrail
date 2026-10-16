package com.cointrail.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.ExpenseDraft
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.data.repo.CatalogKind
import com.cointrail.data.repo.CatalogStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.visible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class EditExpenseUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val amountText: String = "",
    val amountDisplay: String = MoneyInput.empty().display(),
    val categories: List<CatalogItem> = emptyList(),
    val paymentMethods: List<CatalogItem> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedPaymentMethodId: String? = null,
    val note: String = "",
    val noteExpanded: Boolean = false,
    val occurredAt: LocalDateTime? = null,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Backs the edit screen (SPEC §6.3): the amount and selection rules live in [ExpenseDraft], shared
 * with quick-add; what edit itself owns is loading the expense, its timestamp, and writing the
 * change back.
 */
class EditExpenseViewModel(
    private val expenseId: String,
    private val expenses: ExpenseStore,
    private val catalog: CatalogStore,
) : ViewModel() {

    private var draft = ExpenseDraft()
    private var original: Expense? = null

    private val _state = MutableStateFlow(EditExpenseUiState())
    val state: StateFlow<EditExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            catalog.observeAll(CatalogKind.CATEGORIES).map { it.visible() }.collect { categories ->
                _state.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            catalog.observeAll(CatalogKind.PAYMENT_METHODS).map { it.visible() }.collect { paymentMethods ->
                _state.update { it.copy(paymentMethods = paymentMethods) }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val expense = expenses.findById(expenseId)
        if (expense == null) {
            _state.update { it.copy(loading = false, notFound = true) }
            return
        }
        original = expense
        draft = ExpenseDraft(
            amount = MoneyInput.fromMoney(expense.amount),
            categoryId = expense.categoryId,
            paymentMethodId = expense.paymentMethodId,
            note = expense.note.orEmpty(),
            noteExpanded = !expense.note.isNullOrBlank(),
        )
        _state.update {
            it.copy(
                loading = false,
                amountText = draft.amount.text,
                amountDisplay = draft.amount.display(),
                selectedCategoryId = draft.categoryId,
                selectedPaymentMethodId = draft.paymentMethodId,
                note = draft.note,
                noteExpanded = draft.noteExpanded,
                occurredAt = expense.occurredAt,
                canSave = draft.canSave,
            )
        }
    }

    fun onDigit(digit: Char) {
        draft = draft.onDigit(digit)
        publish()
    }

    fun onDecimal() {
        draft = draft.onDecimal()
        publish()
    }

    fun onBackspace() {
        draft = draft.onBackspace()
        publish()
    }

    fun selectCategory(categoryId: String) {
        draft = draft.selectCategory(categoryId)
        publish()
    }

    fun selectPaymentMethod(paymentMethodId: String) {
        draft = draft.selectPaymentMethod(paymentMethodId)
        publish()
    }

    fun setNote(note: String) {
        draft = draft.setNote(note)
        publish()
    }

    fun toggleNoteExpanded() {
        draft = draft.toggleNoteExpanded()
        publish()
    }

    fun onDateSelected(date: LocalDate) {
        _state.update { ui -> ui.occurredAt?.let { ui.copy(occurredAt = it.with(date)) } ?: ui }
    }

    fun onTimeSelected(time: LocalTime) {
        _state.update { ui -> ui.occurredAt?.let { ui.copy(occurredAt = it.with(time)) } ?: ui }
    }

    fun save() {
        val unedited = original ?: return
        val money = draft.money() ?: return
        if (money <= Money.ZERO) return
        val categoryId = draft.categoryId ?: return
        val occurredAt = _state.value.occurredAt ?: return
        viewModelScope.launch {
            expenses.update(
                unedited.copy(
                    amount = money,
                    categoryId = categoryId,
                    note = draft.note.ifBlank { null },
                    paymentMethodId = draft.paymentMethodId,
                    occurredAt = occurredAt,
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun onSavedHandled() {
        _state.update { it.copy(saved = false) }
    }

    private fun publish() {
        _state.update {
            it.copy(
                amountText = draft.amount.text,
                amountDisplay = draft.amount.display(),
                selectedCategoryId = draft.categoryId,
                selectedPaymentMethodId = draft.paymentMethodId,
                note = draft.note,
                noteExpanded = draft.noteExpanded,
                canSave = draft.canSave,
            )
        }
    }
}
