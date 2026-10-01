package com.cointrail.ui.quickadd

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.di.AppContainer

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                NoteField(state, onToggleNote, onNoteChange)
                Spacer(modifier = Modifier.height(12.dp))
            }

            HorizontalDivider()
            Keypad(onDigit = onDigit, onDecimal = onDecimal, onBackspace = onBackspace)
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

@Composable
private fun AmountDisplay(display: String, hasInput: Boolean) {
    val caretTransition = rememberInfiniteTransition(label = "amount-caret")
    val caretAlpha by caretTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "amount-caret-alpha",
    )

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        SectionLabel("Amount")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = display,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasInput) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(modifier = Modifier.width(3.dp))
            Box(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .width(3.dp)
                    .height(36.dp)
                    .alpha(caretAlpha)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

private data class ChipOption(val id: String, val name: String)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(
    options: List<ChipOption>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) },
                label = { Text(option.name) },
            )
        }
    }
}

@Composable
private fun NoteField(
    state: QuickAddUiState,
    onToggleNote: () -> Unit,
    onNoteChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    if (state.noteExpanded) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        OutlinedTextField(
            value = state.note,
            onValueChange = onNoteChange,
            label = { Text("Note") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
    } else {
        TextButton(onClick = onToggleNote) { Text("Add note") }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

private val KEYPAD_ROWS: List<List<String>> = listOf(
    listOf("1", "2", "3"),
    listOf("4", "5", "6"),
    listOf("7", "8", "9"),
    listOf(".", "0", "⌫"),
)

@Composable
private fun Keypad(
    onDigit: (Char) -> Unit,
    onDecimal: () -> Unit,
    onBackspace: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        KEYPAD_ROWS.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    TextButton(
                        onClick = {
                            when (key) {
                                "." -> onDecimal()
                                "⌫" -> onBackspace()
                                else -> onDigit(key[0])
                            }
                        },
                        modifier = Modifier.weight(1f).height(60.dp),
                    ) {
                        Text(
                            text = key,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}
