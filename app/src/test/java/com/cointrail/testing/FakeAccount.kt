package com.cointrail.testing

import com.cointrail.data.account.Account
import com.cointrail.data.account.AccountSwitcher
import com.cointrail.data.account.GoogleSignIn
import com.cointrail.data.account.GoogleSignInResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** An in-memory [AccountSwitcher] that records the calls the settings UI makes. */
class FakeAccountSwitcher(initial: Account? = null) : AccountSwitcher {

    private val state = MutableStateFlow(initial)

    override val account: StateFlow<Account?> = state

    val signIns: MutableList<Account> = mutableListOf()

    var signOutCount: Int = 0
        private set

    var removeCount: Int = 0
        private set

    override suspend fun signIn(account: Account) {
        signIns += account
        state.value = account
    }

    override suspend fun signOut() {
        signOutCount++
        state.value = null
    }

    override suspend fun removeSignedInData() {
        removeCount++
        state.value = null
    }
}

/** A scripted [GoogleSignIn] returning a fixed result and counting attempts. */
class FakeGoogleSignIn(
    override var isConfigured: Boolean = true,
    var result: GoogleSignInResult = GoogleSignInResult.Cancelled,
) : GoogleSignIn {

    var signInCount: Int = 0
        private set

    override suspend fun signIn(): GoogleSignInResult {
        signInCount++
        return result
    }
}
