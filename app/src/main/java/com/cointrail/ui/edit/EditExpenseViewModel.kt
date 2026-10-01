package com.cointrail.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class EditExpenseUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val amountText: String = "",
    val amountDisplay: String = "৳0",
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedPaymentMethodId: String? = null,
    val note: String = "",
    val noteExpanded: Boolean = false,
    val occurredAt: LocalDateTime? = null,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

class EditExpenseViewModel(
    private val expenseId: String,
    private val expenses: ExpenseStore,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
) : ViewModel() {

    private var amount: MoneyInput = MoneyInput.empty()
    private var original: Expense? = null

    private val _state = MutableStateFlow(EditExpenseUiState())
    val state: StateFlow<EditExpenseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            categories.observeAll().collect { list ->
                _state.update { it.copy(categories = list.filter { category -> !category.isHidden }) }
            }
        }
        viewModelScope.launch {
            paymentMethods.observeAll().collect { list ->
                _state.update { it.copy(paymentMethods = list.filter { method -> !method.isHidden }) }
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
        amount = MoneyInput.fromMoney(expense.amount)
        _state.update {
            it.copy(
                loading = false,
                amountText = amount.text,
                amountDisplay = amount.display(),
                selectedCategoryId = expense.categoryId,
                selectedPaymentMethodId = expense.paymentMethodId,
                note = expense.note.orEmpty(),
                noteExpanded = !expense.note.isNullOrBlank(),
                occurredAt = expense.occurredAt,
                canSave = amount.canSave,
            )
        }
    }

    fun onDigit(digit: Char) {
        amount = amount.appendDigit(digit)
        updateAmountState()
    }

    fun onDecimal() {
        amount = amount.appendDecimal()
        updateAmountState()
    }

    fun onBackspace() {
        amount = amount.backspace()
        updateAmountState()
    }

    fun selectCategory(categoryId: String) {
        _state.update { it.copy(selectedCategoryId = categoryId) }
        updateAmountState()
    }

    fun selectPaymentMethod(paymentMethodId: String) {
        _state.update {
            val next = if (it.selectedPaymentMethodId == paymentMethodId) null else paymentMethodId
            it.copy(selectedPaymentMethodId = next)
        }
    }

    fun setNote(note: String) {
        _state.update { it.copy(note = note) }
    }

    fun toggleNoteExpanded() {
        _state.update { it.copy(noteExpanded = !it.noteExpanded) }
    }

    fun onDateSelected(date: LocalDate) {
        _state.update { ui -> ui.occurredAt?.let { ui.copy(occurredAt = it.with(date)) } ?: ui }
    }

    fun onTimeSelected(time: LocalTime) {
        _state.update { ui -> ui.occurredAt?.let { ui.copy(occurredAt = it.with(time)) } ?: ui }
    }

    fun save() {
        val unedited = original ?: return
        val money = amount.toMoney() ?: return
        if (money <= Money.ZERO) return
        val current = _state.value
        val categoryId = current.selectedCategoryId ?: return
        val occurredAt = current.occurredAt ?: return
        viewModelScope.launch {
            expenses.update(
                unedited.copy(
                    amount = money,
                    categoryId = categoryId,
                    note = current.note.ifBlank { null },
                    paymentMethodId = current.selectedPaymentMethodId,
                    occurredAt = occurredAt,
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }

    fun onSavedHandled() {
        _state.update { it.copy(saved = false) }
    }

    private fun updateAmountState() {
        val canSave = amount.canSave && _state.value.selectedCategoryId != null
        _state.update { it.copy(amountText = amount.text, amountDisplay = amount.display(), canSave = canSave) }
    }
}
