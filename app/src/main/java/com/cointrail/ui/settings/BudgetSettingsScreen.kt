package com.cointrail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.di.AppContainer

@Composable
fun BudgetSettingsRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BudgetSettingsViewModel = viewModel(
        factory = remember(container) { budgetSettingsViewModelFactory(container) },
    )
    val state by viewModel.state.collectAsState()

    BudgetSettingsScreen(
        state = state,
        onSetLimit = viewModel::setLimit,
        onClear = viewModel::clear,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun budgetSettingsViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        BudgetSettingsViewModel(
            budgets = container.budgets,
            categories = container.categories,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetSettingsScreen(
    state: BudgetSettingsUiState,
    onSetLimit: (String?, String) -> Unit,
    onClear: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BudgetLimitRow?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                text = "Set an overall monthly budget and optional per-category budgets. " +
                    "Budgets reset each month and unused amounts do not roll over.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.categoryId ?: OVERALL_ROW_KEY }) { row ->
                    BudgetRow(row = row, onClick = { editing = row })
                    HorizontalDivider()
                }
            }
        }
    }

    val row = editing
    if (row != null) {
        BudgetDialog(
            row = row,
            onDismiss = { editing = null },
            onConfirm = { input ->
                onSetLimit(row.categoryId, input)
                editing = null
            },
            onClear = {
                onClear(row.categoryId)
                editing = null
            },
        )
    }
}

@Composable
private fun BudgetRow(row: BudgetLimitRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = row.limit?.format() ?: "Not set",
            style = MaterialTheme.typography.bodyMedium,
            color = if (row.hasLimit) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BudgetDialog(
    row: BudgetLimitRow,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember {
        mutableStateOf(row.limit?.let { MoneyInput.fromMoney(it).text } ?: "")
    }
    val canSave = Money.fromTaka(text)?.let { it > Money.ZERO } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Monthly limit (৳)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (row.hasLimit) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A stable list key for the overall row; the actual value is arbitrary as long as it is unique. */
private const val OVERALL_ROW_KEY = "overall"
