package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.repo.CategoryStore
import com.cointrail.data.repo.PaymentMethodStore
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The two catalog lists the settings screens manage. Both support add, rename and hide. */
enum class CatalogKind(val title: String) {
    CATEGORIES("Categories"),
    PAYMENT_METHODS("Payment methods"),
}

data class CatalogItemUi(
    val id: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
)

data class ManageCatalogUiState(
    val items: List<CatalogItemUi> = emptyList(),
    val newName: String = "",
) {
    val canAdd: Boolean get() = newName.isNotBlank()
}

/**
 * Backs the category and payment-method management screens. Nothing is ever deleted - presets and
 * custom entries alike can only be renamed or hidden, so old expenses keep their names.
 */
class ManageCatalogViewModel(
    private val kind: CatalogKind,
    private val categories: CategoryStore,
    private val paymentMethods: PaymentMethodStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ManageCatalogUiState())
    val state: StateFlow<ManageCatalogUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            observeItems().collect { items -> _state.update { it.copy(items = items) } }
        }
    }

    fun setNewName(name: String) {
        _state.update { it.copy(newName = name) }
    }

    fun add() {
        val name = _state.value.newName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            when (kind) {
                CatalogKind.CATEGORIES -> categories.add(name)
                CatalogKind.PAYMENT_METHODS -> paymentMethods.add(name)
            }
            _state.update { it.copy(newName = "") }
        }
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            when (kind) {
                CatalogKind.CATEGORIES -> categories.rename(id, trimmed)
                CatalogKind.PAYMENT_METHODS -> paymentMethods.rename(id, trimmed)
            }
        }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch {
            when (kind) {
                CatalogKind.CATEGORIES -> categories.setHidden(id, hidden)
                CatalogKind.PAYMENT_METHODS -> paymentMethods.setHidden(id, hidden)
            }
        }
    }

    private fun observeItems(): Flow<List<CatalogItemUi>> = when (kind) {
        CatalogKind.CATEGORIES -> categories.observeAll().map { list -> list.map { it.toUi() } }
        CatalogKind.PAYMENT_METHODS -> paymentMethods.observeAll().map { list -> list.map { it.toUi() } }
    }

    private fun Category.toUi(): CatalogItemUi =
        CatalogItemUi(id = id, name = name, isPreset = isPreset, isHidden = isHidden)

    private fun PaymentMethod.toUi(): CatalogItemUi =
        CatalogItemUi(id = id, name = name, isPreset = isPreset, isHidden = isHidden)
}
