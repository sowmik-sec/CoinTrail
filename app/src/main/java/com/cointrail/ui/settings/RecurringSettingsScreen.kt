package com.cointrail.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.di.AppContainer
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.ui.components.ChipOption
import com.cointrail.ui.components.ChipRow
import com.cointrail.ui.components.SectionLabel

@Composable
fun RecurringSettingsRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecurringSettingsViewModel = viewModel(
        factory = remember(container) { recurringSettingsViewModelFactory(container) },
    )
    val state by viewModel.state.collectAsState()

    RecurringSettingsScreen(
        state = state,
        onCreate = viewModel::create,
        onUpdate = viewModel::update,
        onSetPaused = viewModel::setPaused,
        onDelete = viewModel::delete,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun recurringSettingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        RecurringSettingsViewModel(
            recurring = container.recurring,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringSettingsScreen(
    state: RecurringSettingsUiState,
    onCreate: (String, String, String?, String, Int) -> Unit,
    onUpdate: (String, String, String, String?, String, Int) -> Unit,
    onSetPaused: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringSeriesRow?>(null) }
    var deleting by remember { mutableStateOf<RecurringSeriesRow?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Recurring expenses") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add recurring expense")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "Rent and subscriptions added automatically each month. Each occurrence is an " +
                    "ordinary expense you can edit or delete without affecting the series.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            if (state.series.isEmpty()) {
                Text(
                    text = "No recurring expenses yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.series, key = { it.id }) { row ->
                        SeriesRow(
                            row = row,
                            onEdit = { editing = row },
                            onSetPaused = { paused -> onSetPaused(row.id, paused) },
                            onDelete = { deleting = row },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (creating) {
        RecurringEditorDialog(
            title = "New recurring expense",
            categories = state.categories,
            paymentMethods = state.paymentMethods,
            initial = null,
            onDismiss = { creating = false },
            onConfirm = { amount, categoryId, paymentMethodId, note, day ->
                onCreate(amount, categoryId, paymentMethodId, note, day)
                creating = false
            },
        )
    }

    val editTarget = editing
    if (editTarget != null) {
        RecurringEditorDialog(
            title = "Edit recurring expense",
            categories = state.categories,
            paymentMethods = state.paymentMethods,
            initial = editTarget,
            onDismiss = { editing = null },
            onConfirm = { amount, categoryId, paymentMethodId, note, day ->
                onUpdate(editTarget.id, amount, categoryId, paymentMethodId, note, day)
                editing = null
            },
        )
    }

    val deleteTarget = deleting
    if (deleteTarget != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete recurring expense?") },
            text = { Text("The expenses it already created are kept; only future occurrences stop.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(deleteTarget.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SeriesRow(
    row: RecurringSeriesRow,
    onEdit: () -> Unit,
    onSetPaused: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.amount.format(), style = MaterialTheme.typography.titleMedium)
            val caption = buildList {
                add("Day ${row.dayOfMonth}")
                add(row.categoryName)
                row.paymentMethodName?.let { add(it) }
                if (row.isPaused) add("Paused")
            }.joinToString(" · ")
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = !row.isPaused,
            onCheckedChange = { active -> onSetPaused(!active) },
        )
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun RecurringEditorDialog(
    title: String,
    categories: List<Category>,
    paymentMethods: List<PaymentMethod>,
    initial: RecurringSeriesRow?,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, categoryId: String, paymentMethodId: String?, note: String, dayOfMonth: Int) -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.amount?.let { MoneyInput.fromMoney(it).text } ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfMonth?.toString() ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId) }
    var paymentMethodId by remember { mutableStateOf(initial?.paymentMethodId) }
    var note by remember { mutableStateOf(initial?.note ?: "") }

    val dayValue = day.toIntOrNull()
    val canSave = Money.fromTaka(amount)?.let { it > Money.ZERO } == true &&
        categoryId != null &&
        dayValue != null && dayValue in 1..31

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (৳)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = day,
                    onValueChange = { input -> day = input.filter { it.isDigit() }.take(2) },
                    label = { Text("Day of month (1–31)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Category")
                ChipRow(
                    options = categories.map { ChipOption(it.id, it.name) },
                    selectedId = categoryId,
                    onSelect = { categoryId = it },
                )
                Spacer(modifier = Modifier.height(12.dp))
                SectionLabel("Payment method (optional)")
                ChipRow(
                    options = paymentMethods.map { ChipOption(it.id, it.name) },
                    selectedId = paymentMethodId,
                    onSelect = { id -> paymentMethodId = if (paymentMethodId == id) null else id },
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { categoryId?.let { onConfirm(amount, it, paymentMethodId, note, dayValue!!) } },
                enabled = canSave,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
