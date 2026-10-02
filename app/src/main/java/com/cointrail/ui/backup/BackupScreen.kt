package com.cointrail.ui.backup

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.data.account.Account
import com.cointrail.data.backup.BackupController
import com.cointrail.data.backup.SnapshotRef
import com.cointrail.di.AppContainer
import com.cointrail.ui.components.SectionLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.format.DateTimeFormatter

@Composable
fun BackupRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BackupViewModel = viewModel(
        factory = remember(container) {
            backupViewModelFactory(controller = container.backup, account = container.accounts.account)
        },
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()

    // Drive consent, if a snapshot operation asked for it, is launched from this Activity, then retried.
    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDriveAuthorizationResult(result.resultCode == Activity.RESULT_OK) }
    val driveAuthorizationIntent by viewModel.driveAuthorizationIntent.collectAsState()
    LaunchedEffect(driveAuthorizationIntent) {
        driveAuthorizationIntent?.let {
            driveAuthLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val json = viewModel.buildExportJson()
                viewModel.onExportWritten(json != null && writeText(context.contentResolver, uri, json))
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                readText(context.contentResolver, uri)?.let(viewModel::import)
            }
        }
    }

    BackupScreen(
        state = state,
        onBackupNow = viewModel::backupNow,
        onRestore = viewModel::restore,
        onRefresh = viewModel::refresh,
        onExport = { exportLauncher.launch(viewModel.exportFileName()) },
        onImport = { importLauncher.launch(BACKUP_MIME_TYPES) },
        onDismissMessage = viewModel::dismissMessage,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun backupViewModelFactory(
    controller: BackupController,
    account: StateFlow<Account?>,
) = viewModelFactory {
    initializer {
        BackupViewModel(controller, account)
    }
}

// Providers disagree on the MIME type of a .json file, so accept the common ones and a wildcard
// rather than hiding a backup the user just exported (SPEC §7: import "anywhere").
private val BACKUP_MIME_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")

private suspend fun writeText(resolver: ContentResolver, uri: Uri, text: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            resolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: IOException) {
            false
        }
    }

private suspend fun readText(resolver: ContentResolver, uri: Uri): String? =
    withContext(Dispatchers.IO) {
        try {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        } catch (e: IOException) {
            null
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    state: BackupUiState,
    onBackupNow: () -> Unit,
    onRestore: (String) -> Unit,
    onRefresh: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismissMessage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Backup & restore") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            SectionLabel("Drive backups")
            Text(
                text = "Timestamped full backups are saved to your own Google Drive. Restoring one " +
                    "merges it into this device, so it is safe to run more than once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.signedIn) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(onClick = onBackupNow, enabled = !state.busy) {
                        Text(if (state.busy) "Working…" else "Backup now")
                    }
                    TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh") }
                }
                when {
                    state.loading -> Text(
                        text = "Loading backups…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.snapshots.isEmpty() -> Text(
                        text = "No backups yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> state.snapshots.forEach { snapshot ->
                        SnapshotRow(snapshot = snapshot, enabled = !state.busy, onRestore = onRestore)
                        HorizontalDivider()
                    }
                }
            } else {
                Text(
                    text = "Sign in with Google to store backups in Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            SectionLabel("JSON file")
            Text(
                text = "Export the whole database to a versioned JSON file you can save anywhere " +
                    "and import on any device. The format stays readable for the long term.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onExport, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text("Export to file")
                }
                OutlinedButton(onClick = onImport, enabled = !state.busy, modifier = Modifier.weight(1f)) {
                    Text("Import from file")
                }
            }

            val message = state.message
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDismissMessage)
                        .padding(vertical = 12.dp),
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val snapshotFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
private fun SnapshotRow(snapshot: SnapshotRef, enabled: Boolean, onRestore: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = snapshotFormat.format(snapshot.createdAt),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = { onRestore(snapshot.id) }, enabled = enabled) { Text("Restore") }
    }
}
