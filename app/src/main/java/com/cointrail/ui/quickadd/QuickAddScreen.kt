package com.cointrail.ui.quickadd

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.AmountDisplay
import com.cointrail.ui.components.AmountKeypad
import com.cointrail.ui.components.ChipOption
import com.cointrail.ui.components.ChipRow
import com.cointrail.ui.components.NoteField
import com.cointrail.ui.components.SectionLabel

@Composable
fun QuickAddRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuickAddViewModel = viewModel(
        factory = remember(container) { quickAddViewModelFactory(container) },
    )
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.saved) {
        if (state.saved) {
            viewModel.onSavedHandled()
            onDone()
        }
    }

    QuickAddScreen(
        state = state,
        onDigit = viewModel::onDigit,
        onDecimal = viewModel::onDecimal,
        onBackspace = viewModel::onBackspace,
        onSelectCategory = viewModel::selectCategory,
        onSelectPaymentMethod = viewModel::selectPaymentMethod,
        onToggleNote = viewModel::toggleNoteExpanded,
        onNoteChange = viewModel::setNote,
        onSave = viewModel::save,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun quickAddViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        QuickAddViewModel(
            expenses = container.expenses,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddScreen(
    state: QuickAddUiState,
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectPaymentMethod: (String) -> Unit,
    onToggleNote: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Quick add") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Cancel")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                AmountDisplay(display = state.amountDisplay, hasInput = state.amountText.isNotEmpty())
                SectionLabel("Category")
                ChipRow(
                    options = state.categories.map { ChipOption(it.id, it.name) },
                    selectedId = state.selectedCategoryId,
                    onSelect = onSelectCategory,
                )
                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Payment method (optional)")
                ChipRow(
                    options = state.paymentMethods.map { ChipOption(it.id, it.name) },
                    selectedId = state.selectedPaymentMethodId,
                    onSelect = onSelectPaymentMethod,
                )
                Spacer(modifier = Modifier.height(12.dp))
                NoteField(
                    text = state.note,
                    expanded = state.noteExpanded,
                    onToggle = onToggleNote,
                    onChange = onNoteChange,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            HorizontalDivider()
            AmountKeypad(onDigit = onDigit, onDecimal = onDecimal, onBackspace = onBackspace)
            Button(
                onClick = onSave,
                enabled = state.canSave,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text("Save")
            }
        }
    }
}
