package com.cointrail.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsRoute(
    onManage: (CatalogKind) -> Unit,
    onBudgets: () -> Unit,
    onReminder: () -> Unit,
    onRecurring: () -> Unit,
    onExportCsv: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsScreen(
        onManageCategories = { onManage(CatalogKind.CATEGORIES) },
        onManagePaymentMethods = { onManage(CatalogKind.PAYMENT_METHODS) },
        onBudgets = onBudgets,
        onReminder = onReminder,
        onRecurring = onRecurring,
        onExportCsv = onExportCsv,
        onClose = onClose,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onManageCategories: () -> Unit,
    onManagePaymentMethods: () -> Unit,
    onBudgets: () -> Unit,
    onReminder: () -> Unit,
    onRecurring: () -> Unit,
    onExportCsv: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            SettingsRow(label = CatalogKind.CATEGORIES.title, onClick = onManageCategories)
            HorizontalDivider()
            SettingsRow(label = CatalogKind.PAYMENT_METHODS.title, onClick = onManagePaymentMethods)
            HorizontalDivider()
            SettingsRow(label = "Budgets", onClick = onBudgets)
            HorizontalDivider()
            SettingsRow(label = "Recurring expenses", onClick = onRecurring)
            HorizontalDivider()
            SettingsRow(label = "Daily reminder", onClick = onReminder)
            HorizontalDivider()
            SettingsRow(label = "Export CSV", onClick = onExportCsv)
        }
    }
}

@Composable
private fun SettingsRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
