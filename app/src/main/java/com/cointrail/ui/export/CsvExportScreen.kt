package com.cointrail.ui.export

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.DateField
import com.cointrail.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

@Composable
fun CsvExportRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CsvExportViewModel = viewModel(
        factory = remember(container) { csvExportViewModelFactory(container) },
    )
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val exported = writeCsv(context.contentResolver, uri, viewModel.buildCsv())
                snackbarHostState.showSnackbar(if (exported) "Exported" else "Couldn't export")
            }
        }
    }

    CsvExportScreen(
        state = state,
        snackbarHostState = snackbarHostState,
        onFromChange = viewModel::setFrom,
        onToChange = viewModel::setTo,
        onExport = { saveLauncher.launch(state.fileName) },
        onClose = onDone,
        modifier = modifier,
    )
}

private fun csvExportViewModelFactory(container: AppContainer) = viewModelFactory {
    initializer {
        CsvExportViewModel(
            expenses = container.expenses,
            categories = container.categories,
            paymentMethods = container.paymentMethods,
        )
    }
}

private suspend fun writeCsv(resolver: ContentResolver, uri: Uri, csv: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(csv.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: IOException) {
            false
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsvExportScreen(
    state: CsvExportUiState,
    snackbarHostState: SnackbarHostState,
    onFromChange: (LocalDate) -> Unit,
    onToChange: (LocalDate) -> Unit,
    onExport: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Export CSV") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Export every expense in a date range, both days included, to a CSV you can " +
                    "save anywhere.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(20.dp))

            SectionLabel("From")
            DateField(date = state.from, onDateSelected = onFromChange)
            Spacer(modifier = Modifier.height(16.dp))

            SectionLabel("To")
            DateField(date = state.to, onDateSelected = onToChange)

            if (!state.canExport) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "The start date must be on or before the end date.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.height(28.dp))
            Button(
                onClick = onExport,
                enabled = state.canExport,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Export")
            }
        }
    }
}
