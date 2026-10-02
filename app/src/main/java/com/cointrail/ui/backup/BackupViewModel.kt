package com.cointrail.ui.backup

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.account.Account
import com.cointrail.data.backup.BackupController
import com.cointrail.data.backup.SnapshotRef
import com.cointrail.data.backup.SnapshotResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class BackupUiState(
    val signedIn: Boolean = false,
    val snapshots: List<SnapshotRef> = emptyList(),
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Backs the "Backup & restore" screen (SPEC §7): the account's Drive snapshots with "Backup now"
 * and per-snapshot restore, plus JSON export/import to anywhere. Drive operations prompt for consent
 * the same way sync does; JSON export/import needs no account.
 */
class BackupViewModel(
    private val backup: BackupController,
    account: Flow<Account?>,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : ViewModel() {

    private val snapshots = MutableStateFlow<List<SnapshotRef>>(emptyList())
    private val loading = MutableStateFlow(false)
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingDriveAuth = MutableStateFlow<IntentSender?>(null)

    /** Set when a Drive operation needs the user's consent; the screen launches it, then retries. */
    val driveAuthorizationIntent: StateFlow<IntentSender?> = pendingDriveAuth.asStateFlow()

    private var retryAfterConsent: (suspend () -> Unit)? = null

    val state: StateFlow<BackupUiState> =
        combine(account, snapshots, loading, busy, message) { account, snapshotList, isLoading, isBusy, currentMessage ->
            BackupUiState(
                signedIn = account != null,
                snapshots = snapshotList,
                loading = isLoading,
                busy = isBusy,
                message = currentMessage,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, BackupUiState())

    init {
        refresh()
    }

    fun refresh() {
        perform(setLoading = true) { reload() }
    }

    fun backupNow() {
        perform {
            message.value = null
            when (val result = backup.backupNow()) {
                is SnapshotResult.Success -> {
                    message.value = "Backup created"
                    reload()
                }
                is SnapshotResult.Failed -> message.value = result.message
                is SnapshotResult.AuthorizationRequired -> requestConsent(result.intentSender) { backupNow() }
            }
        }
    }

    fun restore(id: String) {
        perform {
            message.value = null
            when (val result = backup.restoreSnapshot(id)) {
                is SnapshotResult.Success -> message.value = "Restored"
                is SnapshotResult.Failed -> message.value = result.message
                is SnapshotResult.AuthorizationRequired -> requestConsent(result.intentSender) { restore(id) }
            }
        }
    }

    /** Builds the JSON backup document for export, or null (with a message) if it could not. */
    suspend fun buildExportJson(): String? = when (val result = backup.exportJson()) {
        is SnapshotResult.Success -> result.value
        is SnapshotResult.Failed -> {
            message.value = result.message
            null
        }
        is SnapshotResult.AuthorizationRequired -> null
    }

    /** Imports a JSON backup document the user picked, merging it into the local data. */
    fun import(text: String) {
        perform {
            message.value = null
            when (val result = backup.importJson(text)) {
                is SnapshotResult.Success -> message.value = "Imported"
                is SnapshotResult.Failed -> message.value = result.message
                is SnapshotResult.AuthorizationRequired -> Unit
            }
        }
    }

    /** Reports the outcome of writing the exported file, so the screen can show it. */
    fun onExportWritten(success: Boolean) {
        message.value = if (success) "Exported" else "Couldn't export"
    }

    /** Called by the screen after the Drive consent result; a granted result retries the operation. */
    fun onDriveAuthorizationResult(granted: Boolean) {
        pendingDriveAuth.value = null
        val retry = retryAfterConsent ?: return
        retryAfterConsent = null
        if (granted) viewModelScope.launch { retry() }
    }

    fun dismissMessage() {
        message.value = null
    }

    fun exportFileName(): String = "cointrail-backup-${now().toLocalDate()}.json"

    private suspend fun reload() {
        when (val result = backup.listSnapshots()) {
            is SnapshotResult.Success -> snapshots.value = result.value
            is SnapshotResult.Failed -> message.value = result.message
            is SnapshotResult.AuthorizationRequired -> requestConsent(result.intentSender) { refresh() }
        }
    }

    private fun requestConsent(intentSender: IntentSender?, retry: suspend () -> Unit) {
        retryAfterConsent = retry
        pendingDriveAuth.value = intentSender
    }

    /** Runs a backup action once at a time, so a double tap can never race two writes. */
    private fun perform(setLoading: Boolean = false, action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            if (setLoading) loading.value = true
            try {
                action()
            } finally {
                busy.value = false
                if (setLoading) loading.value = false
            }
        }
    }
}
