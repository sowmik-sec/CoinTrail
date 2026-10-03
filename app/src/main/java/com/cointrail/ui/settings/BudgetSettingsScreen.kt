package com.cointrail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.core.Money
import com.cointrail.core.MoneyInput
import com.cointrail.di.AppContainer
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy").withLocale(Locale.ENGLISH)

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
    val stepperMonth by viewModel.stepperMonth.collectAsState()

    BudgetSettingsScreen(
        state = state,
        stepperMonth = stepperMonth,
        onSelectDefault = viewModel::selectDefault,
        onSelectMonth = viewModel::selectMonth,
        onPreviousMonth = viewModel::showPreviousMonth,
        onNextMonth = viewModel::showNextMonth,
        onSetDefaultLimit = viewModel::setDefaultLimit,
        onClearDefault = viewModel::clearDefault,
        onSetMonthLimit = viewModel::setMonthLimit,
        onSetMonthNoBudget = viewModel::setMonthNoBudget,
        onUseDefaultForMonth = viewModel::useDefaultForMonth,
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
    stepperMonth: YearMonth,
    onSelectDefault: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSetDefaultLimit: (String?, String) -> Unit,
    onClearDefault: (String?) -> Unit,
    onSetMonthLimit: (String?, String) -> Unit,
    onSetMonthNoBudget: (String?) -> Unit,
    onUseDefaultForMonth: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<BudgetLimitRow?>(null) }
    val pickingMonth = state.selection is BudgetMonthSelection.Month

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
                text = "Every scope has a default budget — its standing monthly limit. Pick any " +
                    "month, past or future, to give it its own limit, mark it as having no budget, " +
                    "or hand it back to the default. Unused budget never rolls over.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            MonthPicker(
                pickingMonth = pickingMonth,
                stepperMonth = stepperMonth,
                onSelectDefault = onSelectDefault,
                onSelectMonth = onSelectMonth,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.rows, key = { it.categoryId ?: OVERALL_ROW_KEY }) { row ->
                    BudgetRow(row = row, pickingMonth = pickingMonth, onClick = { editing = row })
                    HorizontalDivider()
                }
            }
        }
    }

    val row = editing
    if (row != null) {
        BudgetDialog(
            row = row,
            pickingMonth = pickingMonth,
            pickedMonth = (state.selection as? BudgetMonthSelection.Month)?.month,
            onConfirm = { input ->
                if (pickingMonth) {
                    onSetMonthLimit(row.categoryId, input)
                } else {
                    onSetDefaultLimit(row.categoryId, input)
                }
                editing = null
            },
            secondaryActions = buildList {
                if (pickingMonth) {
                    add(
                        "No budget for this month" to {
                            onSetMonthNoBudget(row.categoryId)
                            editing = null
                        },
                    )
                    if (row.monthState != MonthBudgetState.Inherited) {
                        add(
                            "Use default for this month" to {
                                onUseDefaultForMonth(row.categoryId)
                                editing = null
                            },
                        )
                    }
                } else if (row.hasDefault) {
                    add(
                        "Clear default" to {
                            onClearDefault(row.categoryId)
                            editing = null
                        },
                    )
                }
            },
            onDismiss = { editing = null },
        )
    }
}

/** The month picker: the scopes' default budgets, or any month past or future. */
@Composable
private fun MonthPicker(
    pickingMonth: Boolean,
    stepperMonth: YearMonth,
    onSelectDefault: () -> Unit,
    onSelectMonth: (YearMonth) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = !pickingMonth,
            onClick = onSelectDefault,
            label = { Text("Default") },
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onPreviousMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            text = stepperMonth.format(MONTH_FORMAT),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (pickingMonth) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .weight(1f)
                .clickable { onSelectMonth(stepperMonth) }
                .padding(vertical = 8.dp),
        )
        IconButton(onClick = onNextMonth) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun BudgetRow(
    row: BudgetLimitRow,
    pickingMonth: Boolean,
    onClick: () -> Unit,
) {
    val monthState = row.monthState
    val showsInheritedHint = pickingMonth && monthState is MonthBudgetState.Inherited
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.label, style = MaterialTheme.typography.titleMedium)
            if (showsInheritedHint) {
                Text(
                    text = row.defaultLimit?.let { "Default ${it.format()}" } ?: "No default budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = trailingText(row, pickingMonth),
            style = MaterialTheme.typography.bodyMedium,
            color = if (showsOwnBudget(row, pickingMonth)) {
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

/** The scope's own override limit for the picked month, when one governs. */
private fun overrideLimitOf(row: BudgetLimitRow, pickingMonth: Boolean): Money? {
    val monthState = row.monthState
    return if (pickingMonth && monthState is MonthBudgetState.Override) monthState.limit else null
}

/** The trailing state text: what governs the picked selection for this scope. */
private fun trailingText(row: BudgetLimitRow, pickingMonth: Boolean): String {
    val overrideLimit = overrideLimitOf(row, pickingMonth)
    val noBudget = pickingMonth && row.monthState is MonthBudgetState.NoBudget
    return when {
        // Under Default the row simply shows the standing limit; under a month it names the state.
        !pickingMonth -> row.defaultLimit?.format() ?: "Not set"
        overrideLimit != null -> overrideLimit.format()
        noBudget -> "No budget"
        else -> row.defaultLimit?.let { default -> "Default ${default.format()}" } ?: "Not set"
    }
}

/** Whether the trailing text is this scope's own budget (rather than an inherited or absent one). */
private fun showsOwnBudget(row: BudgetLimitRow, pickingMonth: Boolean): Boolean =
    if (pickingMonth) {
        overrideLimitOf(row, pickingMonth) != null
    } else {
        row.hasDefault
    }

/**
 * The editing dialog. Under Default it saves or clears the scope's default budget; under a picked
 * month it saves the month's override, with "No budget for this month" and "Use default for this
 * month" as distinct secondary actions (Q44, Q47).
 */
@Composable
private fun BudgetDialog(
    row: BudgetLimitRow,
    pickingMonth: Boolean,
    pickedMonth: YearMonth?,
    onConfirm: (String) -> Unit,
    secondaryActions: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
) {
    var text by remember(row.categoryId, pickingMonth, pickedMonth) {
        val presetLimit = when {
            pickingMonth -> overrideLimitOf(row, pickingMonth = true)
            else -> row.defaultLimit
        }
        mutableStateOf(presetLimit?.let { MoneyInput.fromMoney(it).text } ?: "")
    }
    val canSave = Money.fromTaka(text)?.let { it > Money.ZERO } == true

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (pickingMonth) {
                        "Budget for ${pickedMonth?.format(MONTH_FORMAT)}"
                    } else {
                        "Default budget"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Monthly limit (৳)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = canSave) { Text("Save") }
        },
        dismissButton = {
            Row {
                secondaryActions.forEach { (label, action) ->
                    TextButton(onClick = action) { Text(label) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A stable list key for the overall row; the actual value is arbitrary as long as it is unique. */
private const val OVERALL_ROW_KEY = "overall"
