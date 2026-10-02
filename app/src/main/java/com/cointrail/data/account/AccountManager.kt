package com.cointrail.data.account

import android.content.Context
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The account operations the settings UI drives: which account is active, and the three ways it can
 * change. Extracted so the UI can be tested without real databases behind it.
 */
interface AccountSwitcher {

    /** The account currently signed in, or `null` in the no-account ("local") namespace. */
    val account: StateFlow<Account?>

    /** Switches to [account]'s namespace. */
    suspend fun signIn(account: Account)

    /** Leaves the signed-in namespace, keeping its local copy. */
    suspend fun signOut()

    /** Deletes the signed-in account's on-device data and returns to the local namespace. */
    suspend fun removeSignedInData()
}

/**
 * Owns the active account namespace: the persisted [AccountSession] plus the open [AccountData] for
 * whichever account is signed in ("local" when signed out). Sign-in, sign-out and "remove my data
 * from this device" swap or delete the scope, so each Google account keeps its own database
 * (SPEC §7). Signing out closes the scope but leaves its file on disk; removing it deletes the file.
 */
class AccountManager(
    context: Context,
    private val session: AccountSession,
) : AccountSwitcher {

    private val appContext: Context = context.applicationContext

    /**
     * Open scopes by namespace key, kept open across switches rather than closed on the way out, so a
     * still-composing screen is never left querying a database this manager just closed. Only removal
     * of a namespace closes its scope.
     */
    private val scopes = mutableMapOf<String, AccountData>()

    private var active: AccountData = openScope(AccountSession.namespaceKey(session.current.value))

    /**
     * The namespace key of the currently open data. The UI watches this and restarts itself so every
     * screen rebuilds against the new namespace instead of a stale repository.
     */
    private val activeKey = MutableStateFlow(active.key)

    /** The namespace key of the currently open data, so the UI can restart itself when it changes. */
    val accountKey: StateFlow<String> = activeKey.asStateFlow()

    /** The account currently signed in, or `null` in the no-account ("local") namespace. */
    override val account: StateFlow<Account?> = session.current

    /** The open data scope for the active account. */
    val current: AccountData get() = active

    /** Switches to [account]'s namespace. */
    override suspend fun signIn(account: Account) {
        session.signIn(account)
        switchTo(account.key)
    }

    /** Leaves the signed-in namespace, keeping its local copy, and reopens the local namespace. */
    override suspend fun signOut() {
        session.signOut()
        switchTo(CoinTrailDatabase.LOCAL_ACCOUNT_KEY)
    }

    /**
     * Deletes the signed-in account's on-device data and returns to the local namespace. This removes
     * only the local copy of the namespace (SPEC §7); its data in Drive is untouched. Safe to call
     * when already in the local namespace (a no-op).
     */
    override suspend fun removeSignedInData() {
        val keyToRemove = activeKey.value
        if (keyToRemove == CoinTrailDatabase.LOCAL_ACCOUNT_KEY) return

        session.signOut()
        switchTo(CoinTrailDatabase.LOCAL_ACCOUNT_KEY)
        scopes.remove(keyToRemove)?.close()
        appContext.deleteDatabase(CoinTrailDatabase.databaseName(keyToRemove))
    }

    private fun switchTo(key: String) {
        if (key == activeKey.value) return
        active = openScope(key)
        activeKey.value = key
    }

    private fun openScope(key: String): AccountData =
        scopes.getOrPut(key) { AccountData(appContext, key) }
}
