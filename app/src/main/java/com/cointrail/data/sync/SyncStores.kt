package com.cointrail.data.sync

import android.content.Context
import android.content.IntentSender
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDateTime

/**
 * The single merged journal file in the user's Google Drive app folder (SPEC §7). Implementations
 * are the transport only; the merge policy lives in [SyncJournalMerge].
 */
interface SyncRemoteStore {

    /** The current journal text, or null when nothing has been written yet. */
    suspend fun readJournal(): String?

    /** Overwrites the remote journal with [content]. */
    suspend fun writeJournal(content: String)
}

/** A namespace's full-row local state, exchanged with the remote journal (SPEC §7). */
interface SyncLocalStore {

    /**
     * Merges [remote] into this namespace's state in a single transaction and returns the merged
     * journal, which the caller publishes back to the remote. Doing the read, merge and write as
     * one transaction means a local edit made concurrently (a new expense, say) can never be erased
     * by a snapshot taken before it (SPEC §7).
     */
    suspend fun mergeRemote(remote: SyncJournal): SyncJournal
}

/**
 * Device-local sync bookkeeping (the last successful sync time shown in the UI). Not user data:
 * it never syncs (SPEC §7).
 */
interface SyncSettings {

    fun observeLastSyncedAt(): Flow<LocalDateTime?>

    suspend fun markSyncedAt(at: LocalDateTime)
}

/** Stores the last successful sync time as an ISO string in shared preferences. */
class SharedPreferencesSyncSettings(context: Context) : SyncSettings {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun observeLastSyncedAt(): Flow<LocalDateTime?> = state

    override suspend fun markSyncedAt(at: LocalDateTime) {
        prefs.edit().putString(KEY_LAST_SYNCED, at.toString()).apply()
        state.value = at
    }

    private fun read(): LocalDateTime? =
        prefs.getString(KEY_LAST_SYNCED, null)
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    private companion object {
        const val PREFS_NAME = "cointrail_sync"
        const val KEY_LAST_SYNCED = "last_synced_at"
    }
}

/**
 * Signals that Drive access needs the user's consent. A UI caller launches [intentSender] and then
 * retries; a background caller can only report it as a failed status (SPEC §7).
 */
class SyncAuthorizationRequired(
    val intentSender: IntentSender?,
) : Exception("Google Drive access is not granted.")
