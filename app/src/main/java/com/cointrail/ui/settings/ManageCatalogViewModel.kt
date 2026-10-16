package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.repo.CatalogKind
import com.cointrail.data.repo.CatalogStore
import com.cointrail.domain.model.CatalogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManageCatalogUiState(
    val items: List<CatalogItem> = emptyList(),
    val newName: String = "",
) {
    val canAdd: Boolean get() = newName.isNotBlank()
}

/**
 * Backs the category and payment-method management screens. Nothing is ever deleted - presets and
 * custom entries alike can only be renamed or hidden, so old expenses keep their names. Both lists
 * come from the one catalog store, keyed by [CatalogKind], so every operation is stated once.
 */
class ManageCatalogViewModel(
    private val kind: CatalogKind,
    private val catalog: CatalogStore,
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
            catalog.add(kind, name)
            _state.update { it.copy(newName = "") }
        }
    }

    fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            catalog.rename(kind, id, trimmed)
        }
    }

    fun setHidden(id: String, hidden: Boolean) {
        viewModelScope.launch {
            catalog.setHidden(kind, id, hidden)
        }
    }

    private fun observeItems(): Flow<List<CatalogItem>> = catalog.observeAll(kind)
}
