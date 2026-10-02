package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.account.AccountSwitcher
import com.cointrail.data.account.GoogleSignIn
import com.cointrail.data.account.GoogleSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountSettingsUiState(
    val signedIn: Boolean = false,
    val email: String? = null,
    val displayName: String? = null,
    val signInConfigured: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
)

/**
 * Backs the Google account section of Settings: who is signed in, and the three actions that change
 * it — sign in, sign out, and remove this device's copy of the account's data (SPEC §7).
 */
class AccountSettingsViewModel(
    private val accounts: AccountSwitcher,
    private val googleSignIn: GoogleSignIn,
) : ViewModel() {

    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    val state: StateFlow<AccountSettingsUiState> =
        combine(accounts.account, busy, message) { account, isBusy, currentMessage ->
            AccountSettingsUiState(
                signedIn = account != null,
                email = account?.email,
                displayName = account?.displayName,
                signInConfigured = googleSignIn.isConfigured,
                busy = isBusy,
                message = currentMessage,
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
