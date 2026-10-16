package com.cointrail.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.data.repo.CatalogKind
import com.cointrail.data.repo.CatalogStore
import com.cointrail.data.repo.ExpenseStore
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.visible
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class QuickAddUiState(
    val amountText: String = "",
    val amountDisplay: String = "৳0",
    val categories: List<CatalogItem> = emptyList(),
    val paymentMethods: List<CatalogItem> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedPaymentMethodId: String? = null,
    val note: String = "",
    val noteExpanded: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

class QuickAddViewModel(
    private val expenses: ExpenseStore,
    private val catalog: CatalogStore,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private var amount: MoneyInput = MoneyInput.empty()

    private val _state = MutableStateFlow(QuickAddUiState())
    val state: StateFlow<QuickAddUiState> = _state.asStateFlow()

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

    fun save() {
        val money = amount.toMoney() ?: return
        if (money <= Money.ZERO) return
        val current = _state.value
        val categoryId = current.selectedCategoryId ?: return
        viewModelScope.launch {
            expenses.add(
                amount = money,
                categoryId = categoryId,
                note = current.note.ifBlank { null },
                paymentMethodId = current.selectedPaymentMethodId,
                occurredAt = now(),
            )
            amount = MoneyInput.empty()
            _state.update {
                it.copy(
                    amountText = "",
                    amountDisplay = MoneyInput.empty().display(),
                    selectedCategoryId = null,
                    selectedPaymentMethodId = null,
                    note = "",
                    noteExpanded = false,
                    canSave = false,
                    saved = true,
                )
            }
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
