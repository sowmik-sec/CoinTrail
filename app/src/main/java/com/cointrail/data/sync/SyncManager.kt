package com.cointrail.data.sync

import android.content.IntentSender
import com.cointrail.data.account.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/** What the account/settings UI shows about the last or in-flight sync (SPEC §7). */
sealed interface SyncStatus {

    /** No Google account is signed in, so there is nothing to sync. */
    data object Unavailable : SyncStatus

    /** Signed in and ready, but not syncing right now. */
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class Synced(val at: LocalDateTime) : SyncStatus

    data class Failed(val message: String) : SyncStatus
}

/** The result of one manual [SyncManager.syncNow], so a UI can react (e.g. launch Drive consent). */
sealed interface SyncOutcome {

    data object Done : SyncOutcome

    data class Failed(val message: String) : SyncOutcome

    data class AuthorizationRequired(val intentSender: IntentSender?) : SyncOutcome
}

/** The trim of [SyncManager] the settings UI needs, so it can be faked in tests. */
interface SyncController {

    val status: StateFlow<SyncStatus>

    /** The last successful sync time, or null if none has happened yet. */
    val lastSyncedAt: Flow<LocalDateTime?>

    suspend fun syncNow(): SyncOutcome
}

/**
 * Runs one sync cycle: read the remote journal, merge it with the local full-row state
 * last-write-wins, write the merged state back locally and remotely (SPEC §7).
 *
 * Because the local database is the queue — every change is already persisted with its `updatedAt` —
 * a device can be offline for days and still lose nothing: the next cycle pushes everything it has
 * and the merge re-propagates anything a concurrent write missed. The merge is idempotent, so
 * repeated runs converge rather than oscillate.
 *
 * Sync is per active Google account; the local namespace (signed out) has no remote and is reported
 * as [SyncStatus.Unavailable].
 */
class SyncManager(
    private val account: StateFlow<Account?>,
    private val localStore: () -> SyncLocalStore?,
    private val remoteStore: (Account) -> SyncRemoteStore?,
    private val settings: SyncSettings,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : SyncController {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    override val lastSyncedAt: Flow<LocalDateTime?> = settings.observeLastSyncedAt()

    override suspend fun syncNow(): SyncOutcome {
        val signedIn = account.value
        val remote = signedIn?.let { remoteStore(it) }
        val local = localStore()
        if (signedIn == null || remote == null || local == null) {
            _status.value = SyncStatus.Unavailable
            return SyncOutcome.Failed(SIGN_IN_REQUIRED)
        }

        _status.value = SyncStatus.Syncing
        return try {
            val remoteJournal = remote.readJournal()?.let(SyncJournalCodec::decode) ?: SyncJournal.EMPTY
            // The local store merges and persists in one transaction and returns the merged state.
            val merged = local.mergeRemote(remoteJournal)
            remote.writeJournal(SyncJournalCodec.encode(merged))

            val at = now()
            settings.markSyncedAt(at)
            _status.value = SyncStatus.Synced(at)
            SyncOutcome.Done
        } catch (e: SyncAuthorizationRequired) {
            _status.value = SyncStatus.Failed(DRIVE_PERMISSION_REQUIRED)
            SyncOutcome.AuthorizationRequired(e.intentSender)
        } catch (e: Exception) {
            val message = e.message?.takeIf { it.isNotBlank() } ?: "Sync failed."
            _status.value = SyncStatus.Failed(message)
            SyncOutcome.Failed(message)
        }
    }

    companion object {
        const val SIGN_IN_REQUIRED: String = "Sign in to sync."
        const val DRIVE_PERMISSION_REQUIRED: String = "Google Drive permission is needed to sync."
    }
}
