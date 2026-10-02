package com.cointrail.data.account

import android.content.Context
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The device-local record of which Google account is active. `null` means no account: the app runs
 * in its "local" namespace and is fully usable without signing in (SPEC §7). This preference is not
 * user data and never syncs.
 */
interface AccountSession {

    /** The active account, or `null` in the no-account namespace. */
    val current: StateFlow<Account?>

    /** Switches to [account]'s namespace (the caller swaps the open data scope to match). */
    suspend fun signIn(account: Account)

    /** Leaves [current]'s namespace, keeping its local data on disk. */
    suspend fun signOut()

    companion object {
        /** The database namespace for [account]; the local namespace when signed out. */
        fun namespaceKey(account: Account?): String = account?.key ?: CoinTrailDatabase.LOCAL_ACCOUNT_KEY
    }
}

/** Persists the active account in shared preferences so it survives process death. */
class SharedPreferencesAccountSession(context: Context) : AccountSession {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val current: StateFlow<Account?> = state

    override suspend fun signIn(account: Account) {
        prefs.edit()
            .putString(KEY_ACCOUNT, account.key)
            .putString(KEY_EMAIL, account.email)
            .putString(KEY_NAME, account.displayName)
            .apply()
        state.value = account
    }

    override suspend fun signOut() {
        prefs.edit().remove(KEY_ACCOUNT).remove(KEY_EMAIL).remove(KEY_NAME).apply()
        state.value = null
    }

    private fun read(): Account? {
        val key = prefs.getString(KEY_ACCOUNT, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        return Account(key = key, email = email, displayName = prefs.getString(KEY_NAME, null))
    }

    private companion object {
        const val PREFS_NAME = "cointrail_account"
        const val KEY_ACCOUNT = "account_key"
        const val KEY_EMAIL = "account_email"
        const val KEY_NAME = "account_display_name"
    }
}
