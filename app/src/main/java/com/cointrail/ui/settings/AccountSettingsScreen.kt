package com.cointrail.ui.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cointrail.data.account.AccountSwitcher
import com.cointrail.data.account.GoogleSignIn
import com.cointrail.data.sync.SyncController
import com.cointrail.data.sync.SyncStatus
import com.cointrail.di.AppContainer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun AccountSettingsRoute(
    container: AppContainer,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The Activity context is what Credential Manager needs to show its account picker.
    val context = LocalContext.current
    val viewModel: AccountSettingsViewModel = viewModel(
        factory = remember(container, context) {
            accountSettingsViewModelFactory(
                accounts = container.accounts,
                googleSignIn = container.googleSignIn(context),
                sync = container.sync,
            )
        },
    )
    val state by viewModel.state.collectAsState()

    // Drive consent, if a sync asked for it, is launched from this Activity, then the sync retries.
    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onDriveAuthorizationResult(result.resultCode == Activity.RESULT_OK) }
    val driveAuthorizationIntent by viewModel.driveAuthorizationIntent.collectAsState()
    LaunchedEffect(driveAuthorizationIntent) {
        driveAuthorizationIntent?.let {
            driveAuthLauncher.launch(IntentSenderRequest.Builder(it).build())
        }
    }

    AccountSettingsScreen(
        state = state,
        onSignIn = viewModel::signIn,
        onSignOut = viewModel::signOut,
        onRemoveData = viewModel::removeData,
        onSyncNow = viewModel::syncNow,
        onDismissMessage = viewModel::dismissMessage,
        onClose = onDone,
        modifier = modifier,
    )
}

private fun accountSettingsViewModelFactory(
    accounts: AccountSwitcher,
    googleSignIn: GoogleSignIn,
    sync: SyncController,
) = viewModelFactory {
    initializer {
        AccountSettingsViewModel(
            accounts = accounts,
            googleSignIn = googleSignIn,
            sync = sync,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSettingsScreen(
    state: AccountSettingsUiState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onRemoveData: () -> Unit,
    onSyncNow: () -> Unit,
    onDismissMessage: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmingRemove by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Google account") },
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
                text = "CoinTrail works without an account. Signing in keeps this account's data " +
                    "separate and syncs it through your own Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            HorizontalDivider()

            if (state.signedIn) {
                AccountSummary(email = state.email, displayName = state.displayName)
                HorizontalDivider()
                SyncSection(
                    status = state.syncStatus,
                    lastSyncedAt = state.lastSyncedAt,
                    enabled = !state.busy,
                    onSyncNow = onSyncNow,
                )
                HorizontalDivider()
                ActionRow(label = "Sign out", enabled = !state.busy, onClick = onSignOut)
                HorizontalDivider()
                ActionRow(
                    label = "Remove my data from this device",
                    enabled = !state.busy,
                    destructive = true,
                    onClick = { confirmingRemove = true },
                )
            } else {
                SignInSection(
                    configured = state.signInConfigured,
                    busy = state.busy,
                    onSignIn = onSignIn,
                )
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
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }

    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text("Remove my data?") },
            text = {
                Text(
                    "This permanently deletes this account's data stored on this device and signs " +
                        "you out. It does not touch anything already saved to Google Drive.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRemove = false
                        onRemoveData()
                    },
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemove = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AccountSummary(email: String?, displayName: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(
            text = "Signed in as",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = email ?: displayName ?: "Google account",
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun SignInSection(configured: Boolean, busy: Boolean, onSignIn: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Button(onClick = onSignIn, enabled = configured && !busy) {
            Text(if (busy) "Signing in…" else "Sign in with Google")
        }
        if (!configured) {
            Text(
                text = "Google sign-in is not set up for this build. Add GOOGLE_WEB_CLIENT_ID to " +
                    "local.properties and rebuild to enable it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SyncSection(
    status: SyncStatus,
    lastSyncedAt: LocalDateTime?,
    enabled: Boolean,
    onSyncNow: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(text = "Drive sync", style = MaterialTheme.typography.titleMedium)
        Text(
            text = syncStatusLabel(status, lastSyncedAt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
        Button(
            onClick = onSyncNow,
            enabled = enabled && status != SyncStatus.Syncing,
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Text(if (status == SyncStatus.Syncing) "Syncing…" else "Sync now")
        }
    }
}

private val syncTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun syncStatusLabel(status: SyncStatus, lastSyncedAt: LocalDateTime?): String = when (status) {
    SyncStatus.Unavailable -> "Sign in to sync."
    SyncStatus.Idle -> lastSyncedAt?.let { "Last synced ${syncTimeFormat.format(it)}" } ?: "Not synced yet."
    SyncStatus.Syncing -> "Syncing…"
    is SyncStatus.Synced -> "Last synced ${syncTimeFormat.format(status.at)}"
    is SyncStatus.Failed -> status.message
}

@Composable
private fun ActionRow(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
    }
}
