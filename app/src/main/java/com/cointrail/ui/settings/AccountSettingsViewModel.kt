package com.cointrail.ui.settings

import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.account.AccountSwitcher
import com.cointrail.data.account.GoogleSignIn
import com.cointrail.data.account.GoogleSignInResult
import com.cointrail.data.sync.SyncController
import com.cointrail.data.sync.SyncOutcome
import com.cointrail.data.sync.SyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

data class AccountSettingsUiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val signInConfigured: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val syncStatus: SyncStatus = SyncStatus.Idle,
    val lastSyncedAt: LocalDateTime? = null,
)

/**
 * Backs the Google account section of Settings: who is signed in, the three actions that change it
 * — sign in, sign out, and remove this device's copy of the account's data — and Drive sync, which
 * is only meaningful while signed in (SPEC §7).
 */
class AccountSettingsViewModel(
    private val accounts: AccountSwitcher,
    private val googleSignIn: GoogleSignIn,
    private val sync: SyncController,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingDriveAuth = MutableStateFlow<IntentSender?>(null)

    /** Set when a sync needs the user's Drive consent; the screen launches it, then retries. */
    val driveAuthorizationIntent: StateFlow<IntentSender?> = pendingDriveAuth.asStateFlow()

    val state: StateFlow<AccountSettingsUiState> =
        combine(
            accounts.account,
            busy,
            message,
            sync.status,
            sync.lastSyncedAt,
        ) { account, isBusy, currentMessage, syncStatus, lastSynced ->
            AccountSettingsUiState(
                signedIn = account != null,
                email = account?.email,
                displayName = account?.displayName,
                signInConfigured = googleSignIn.isConfigured,
                busy = isBusy,
                message = currentMessage,
                syncStatus = syncStatus,
                lastSyncedAt = lastSynced,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            AccountSettingsUiState(signInConfigured = googleSignIn.isConfigured),
        )

    /** Runs the Google picker, then switches the namespace to the chosen account. */
    fun signIn() {
        perform {
            message.value = null
            when (val result = googleSignIn.signIn()) {
                is GoogleSignInResult.Success -> accounts.signIn(result.account)
                GoogleSignInResult.Cancelled -> Unit
                is GoogleSignInResult.Failed -> message.value = result.message
            }
        }
    }

    fun signOut() {
        perform { accounts.signOut() }
    }

    fun removeData() {
        perform { accounts.removeSignedInData() }
    }

    /** Runs one Drive sync, surfacing success silently, a failure as a message, or a consent prompt. */
    fun syncNow() {
        perform {
            message.value = null
            when (val outcome = sync.syncNow()) {
                SyncOutcome.Done -> Unit
                is SyncOutcome.Failed -> message.value = outcome.message
                is SyncOutcome.AuthorizationRequired -> pendingDriveAuth.value = outcome.intentSender
            }
        }
    }

    /** Called by the screen after the Drive consent result; a granted result retries the sync. */
    fun onDriveAuthorizationResult(granted: Boolean) {
        pendingDriveAuth.value = null
        if (granted) syncNow()
    }

    fun dismissMessage() {
        message.value = null
    }

    /** Runs an account action once at a time, so a double tap can never race two namespace swaps. */
    private fun perform(action: suspend () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                action()
            } finally {
                busy.value = false
            }
        }
    }
}
