package com.cointrail.ui.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.core.ExpenseDraft
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
    val amountDisplay: String = MoneyInput.empty().display(),
    val categories: List<CatalogItem> = emptyList(),
    val paymentMethods: List<CatalogItem> = emptyList(),
    val selectedCategoryId: String? = null,
    val selectedPaymentMethodId: String? = null,
    val note: String = "",
    val noteExpanded: Boolean = false,
    val canSave: Boolean = false,
    val saved: Boolean = false,
)

/**
 * Backs quick-add (SPEC §6.2): the amount and selection rules live in [ExpenseDraft], shared with
 * the edit screen; what quick-add itself owns is the picker feed and saving plus resetting the
 * draft for the next entry.
 */
class QuickAddViewModel(
    private val expenses: ExpenseStore,
    private val catalog: CatalogStore,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private var draft = ExpenseDraft()

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

    fun save() {
        val money = draft.money() ?: return
        if (money <= Money.ZERO) return
        val categoryId = draft.categoryId ?: return
        viewModelScope.launch {
            expenses.add(
                amount = money,
                categoryId = categoryId,
                note = draft.note.ifBlank { null },
                paymentMethodId = draft.paymentMethodId,
                occurredAt = now(),
            )
            draft = ExpenseDraft()
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
