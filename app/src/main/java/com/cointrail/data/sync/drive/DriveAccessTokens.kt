package com.cointrail.data.sync.drive

import android.content.Context
import com.cointrail.data.account.Account
import com.cointrail.data.sync.SyncAuthorizationRequired
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import android.accounts.Account as AndroidAccount

/**
 * Supplies an OAuth access token for the Drive app-data scope (SPEC §7). Separated from the REST
 * transport so the token lifecycle can be swapped without touching the wire format.
 */
interface DriveAccessTokens {

    /** A token for the signed-in account, or throws [SyncAuthorizationRequired] if consent is due. */
    suspend fun accessToken(): String
}

/**
 * Google Identity Services implementation of [DriveAccessTokens]. It authorizes the already
 * signed-in account for `drive.appdata` — the app's own private Drive folder — and returns the
 * access token. Once the user has granted the scope the call is silent, as [AuthorizationResult]
 * returns a token directly; before that it returns a resolution [android.app.PendingIntent], which
 * is handed to the UI as [SyncAuthorizationRequired].
 */
class GoogleDriveAccessTokens(
    private val context: Context,
    private val account: Account,
) : DriveAccessTokens {

    override suspend fun accessToken(): String {
        val request = AuthorizationRequest.builder()
            .setAccount(AndroidAccount(account.email, GOOGLE_ACCOUNT_TYPE))
            .setRequestedScopes(listOf(Scope(DRIVE_APP_DATA_SCOPE)))
            .build()

        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        result.accessToken?.takeIf { it.isNotBlank() }?.let { return it }

        throw SyncAuthorizationRequired(result.pendingIntent?.intentSender)
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    companion object {
        /** The scope that grants access to the app's hidden app-data folder, and nothing else. */
        const val DRIVE_APP_DATA_SCOPE: String = "https://www.googleapis.com/auth/drive.appdata"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
