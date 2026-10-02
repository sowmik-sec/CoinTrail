package com.cointrail.data.account

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import org.json.JSONObject
import java.util.Base64

/** The outcome of an optional Google sign-in attempt (SPEC §7). */
sealed interface GoogleSignInResult {

    /** A verified Google account, ready to become the active namespace. */
    data class Success(val account: Account) : GoogleSignInResult

    /** The user dismissed the Google account picker; nothing changes. */
    data object Cancelled : GoogleSignInResult

    /** Sign-in could not run or complete; [message] is safe to show the user. */
    data class Failed(val message: String) : GoogleSignInResult
}

/**
 * Acquires a verified Google account for optional sign-in (SPEC §7). Wrapped behind an interface so
 * the account/namespace logic is testable without the Play Services credential flow.
 */
interface GoogleSignIn {

    /** Whether this build has the OAuth client ID needed to run sign-in at all. */
    val isConfigured: Boolean

    suspend fun signIn(): GoogleSignInResult
}

/**
 * Credential Manager implementation of [GoogleSignIn]. Needs a Google OAuth *web* client ID as the
 * server client ID, supplied at build time via `GOOGLE_WEB_CLIENT_ID` in local.properties. [context]
 * must be an Activity context — Credential Manager shows its picker from it. Until a client ID is
 * set, [isConfigured] is false, [signIn] reports [NOT_CONFIGURED], and the app stays fully usable
 * without an account.
 */
class CredentialManagerGoogleSignIn(
    private val context: Context,
    private val serverClientId: String,
) : GoogleSignIn {

    override val isConfigured: Boolean get() = serverClientId.isNotBlank()

    override suspend fun signIn(): GoogleSignInResult {
        if (!isConfigured) return GoogleSignInResult.Failed(NOT_CONFIGURED)

        return try {
            val credential = CredentialManager.create(context)
                .getCredential(context, request())
            val idToken = GoogleIdTokenCredential.createFrom(credential.credential.data)
            val email = emailFromIdToken(idToken.idToken) ?: idToken.id
            GoogleSignInResult.Success(Account.fromGoogle(email, idToken.displayName))
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (e: GoogleIdTokenParsingException) {
            GoogleSignInResult.Failed("Google returned a credential CoinTrail could not read.")
        } catch (e: GetCredentialException) {
            GoogleSignInResult.Failed(e.message ?: "Google sign-in failed.")
        }
    }

    /**
     * Reads the email claim from the already-verified ID token, so both the namespace key and the
     * account label are human-readable. Falls back to the opaque account id if it cannot be read.
     */
    private fun emailFromIdToken(idToken: String): String? = runCatching {
        val payload = idToken.split('.').getOrNull(1) ?: return@runCatching null
        val json = String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)
        JSONObject(json).optString("email").takeIf { it.isNotBlank() }
    }.getOrNull()

    private fun request(): GetCredentialRequest {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(serverClientId)
            .setAutoSelectEnabled(false)
            .build()
        return GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
    }

    companion object {
        /** Shown when the build has no OAuth client ID, so sign-in cannot run at all. */
        const val NOT_CONFIGURED: String =
            "Google sign-in is not set up for this build. Add GOOGLE_WEB_CLIENT_ID to local.properties."
    }
}
