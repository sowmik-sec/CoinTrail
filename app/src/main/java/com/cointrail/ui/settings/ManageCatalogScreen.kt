package com.cointrail.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.di.AppContainer

@Composable
fun ManageCatalogRoute(
    kind: CatalogKind,
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ManageCatalogViewModel = viewModel(
        key = "manage-${kind.name}",
        factory = remember(container, kind) { manageCatalogViewModelFactory(container, kind) },
    )
    val state by viewModel.state.collectAsState()

    ManageCatalogScreen(
        kind = kind,
        state = state,
        onNewNameChange = viewModel::setNewName,
        onAdd = viewModel::add,
        onRename = viewModel::rename,
        onSetHidden = viewModel::setHidden,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun manageCatalogViewModelFactory(container: AppContainer, kind: CatalogKind) = viewModelFactory {
    initializer {
        ManageCatalogViewModel(
            kind = kind,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCatalogScreen(
    kind: CatalogKind,
    state: ManageCatalogUiState,
    onNewNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: (String, String) -> Unit,
    onSetHidden: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameTarget by remember { mutableStateOf<CatalogItemUi?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(kind.title) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            AddRow(
                value = state.newName,
                canAdd = state.canAdd,
                onValueChange = onNewNameChange,
                onAdd = onAdd,
            )
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.items, key = { it.id }) { item ->
                    CatalogItemRow(
                        item = item,
                        onRename = { renameTarget = item },
                        onSetHidden = { hidden -> onSetHidden(item.id, hidden) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    val target = renameTarget
    if (target != null) {
        RenameDialog(
            initialName = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRename(target.id, name)
                renameTarget = null
            },
        )
    }
}

@Composable
private fun AddRow(
    value: String,
    canAdd: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("New name") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAdd, enabled = canAdd) { Text("Add") }
    }
}

@Composable
private fun CatalogItemRow(
    item: CatalogItemUi,
    onRename: () -> Unit,
    onSetHidden: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
        ) {
            Text(text = item.name, style = MaterialTheme.typography.titleMedium)
            val caption = buildList {
                if (item.isPreset) add("Preset")
                if (item.isHidden) add("Hidden")
            }.joinToString(" · ")
            if (caption.isNotEmpty()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRename) {
            Icon(Icons.Filled.Edit, contentDescription = "Rename ${item.name}")
        }
        Switch(
            checked = !item.isHidden,
            onCheckedChange = { visible -> onSetHidden(!visible) },
        )
    }
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
