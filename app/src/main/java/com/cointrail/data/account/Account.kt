package com.cointrail.data.account

/**
 * A Google account the app can be signed into. All local data is namespaced by [key], so two
 * accounts on one device never see each other's expenses (SPEC §7). The key is the normalized
 * email so it is stable across devices and readable in the on-disk database name.
 */
data class Account(
    val key: String,
    val email: String,
    val displayName: String? = null,
) {
    companion object {
        /** Builds an account from a verified Google sign-in, normalizing the email into the key. */
        fun fromGoogle(email: String, displayName: String? = null): Account {
            val trimmed = email.trim()
            return Account(
                key = trimmed.lowercase(),
                email = trimmed,
                displayName = displayName?.takeIf { it.isNotBlank() },
            )
        }
    }
}
