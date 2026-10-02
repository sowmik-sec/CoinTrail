package com.cointrail.data.backup

import android.content.IntentSender
import com.cointrail.data.account.Account
import com.cointrail.data.sync.SyncAuthorizationRequired
import com.cointrail.data.sync.SyncJournal
import com.cointrail.data.sync.SyncLocalStore
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime

/** The result of a backup operation, so a UI can react (e.g. launch Drive consent). */
sealed interface SnapshotResult<out T> {

    data class Success<T>(val value: T) : SnapshotResult<T>

    data class Failed(val message: String) : SnapshotResult<Nothing>

    data class AuthorizationRequired(val intentSender: IntentSender?) : SnapshotResult<Nothing>
}

/** The backup operations the settings UI drives, so it can be tested against a fake. */
interface BackupController {

    /** The whole database as a versioned JSON backup document; works without a Google account. */
    suspend fun exportJson(): SnapshotResult<String>

    /** Merges a JSON backup document into the local data, last-write-wins; works without an account. */
    suspend fun importJson(text: String): SnapshotResult<Unit>

    /** Lists the timestamped snapshots already in the account's Drive, newest first. */
    suspend fun listSnapshots(): SnapshotResult<List<SnapshotRef>>

    /** Writes a new timestamped snapshot of the whole database to the account's Drive. */
    suspend fun backupNow(): SnapshotResult<SnapshotRef>

    /** Merges a Drive snapshot into the local data, last-write-wins (idempotent). */
    suspend fun restoreSnapshot(id: String): SnapshotResult<Unit>
}

/**
 * Backups on top of sync (SPEC §7): timestamped full snapshots in Drive, plus manual versioned-JSON
 * export/import to anywhere via the document picker.
 *
 * Everything durable funnels through the same last-write-wins merge the sync cycle uses, which is
 * what makes restore idempotent and safe on both a fresh and an existing device — restoring twice,
 * or into a device that already has newer edits, never destroys data. The JSON export/import pair
 * needs no account: it reads and merges the active namespace's own database.
 */
class BackupManager(
    private val account: StateFlow<Account?>,
    private val localStore: () -> SyncLocalStore?,
    private val snapshotStore: (Account) -> SnapshotRemoteStore?,
    private val now: () -> LocalDateTime = { LocalDateTime.now() },
) : BackupController {

    override suspend fun exportJson(): SnapshotResult<String> {
        val local = localStore() ?: return SnapshotResult.Failed(NO_LOCAL_DATA)
        return attempt { SnapshotResult.Success(BackupCodec.encode(local.read(), now())) }
    }

    override suspend fun importJson(text: String): SnapshotResult<Unit> {
        val local = localStore() ?: return SnapshotResult.Failed(NO_LOCAL_DATA)
        return attempt {
            local.mergeRemote(BackupCodec.decode(text).journal)
            SnapshotResult.Success(Unit)
        }
    }

    override suspend fun listSnapshots(): SnapshotResult<List<SnapshotRef>> {
        // Signed out is a normal state, not a failure: the app works with no account (SPEC §7), so
        // there is simply nothing to list. The UI shows its own "sign in" hint from the account flow.
        val signedIn = account.value ?: return SnapshotResult.Success(emptyList())
        val remote = snapshotStore(signedIn) ?: return SnapshotResult.Success(emptyList())
        return attempt { SnapshotResult.Success(remote.list()) }
    }

    override suspend fun backupNow(): SnapshotResult<SnapshotRef> {
        val remote = snapshotStore(account.value ?: return SnapshotResult.Failed(SIGN_IN_REQUIRED))
            ?: return SnapshotResult.Failed(SIGN_IN_REQUIRED)
        val local = localStore() ?: return SnapshotResult.Failed(NO_LOCAL_DATA)
        return attempt {
            val at = now()
            SnapshotResult.Success(remote.write(BackupCodec.encode(local.read(), at), at))
        }
    }

    override suspend fun restoreSnapshot(id: String): SnapshotResult<Unit> {
        val remote = snapshotStore(account.value ?: return SnapshotResult.Failed(SIGN_IN_REQUIRED))
            ?: return SnapshotResult.Failed(SIGN_IN_REQUIRED)
        val local = localStore() ?: return SnapshotResult.Failed(NO_LOCAL_DATA)
        return attempt {
            val snapshot: SyncJournal = BackupCodec.decode(remote.read(id)).journal
            local.mergeRemote(snapshot)
            SnapshotResult.Success(Unit)
        }
    }

    /** Runs [block], turning Drive consent and any failure into a result the UI can show. */
    private inline fun <T> attempt(block: () -> SnapshotResult<T>): SnapshotResult<T> =
        try {
            block()
        } catch (e: SyncAuthorizationRequired) {
            SnapshotResult.AuthorizationRequired(e.intentSender)
        } catch (e: Exception) {
            SnapshotResult.Failed(e.message?.takeIf { it.isNotBlank() } ?: BACKUP_FAILED)
        }

    companion object {
        const val SIGN_IN_REQUIRED: String = "Sign in to back up to Google Drive."
        const val NO_LOCAL_DATA: String = "No local data to back up."
        const val BACKUP_FAILED: String = "Backup failed."
    }
}
