package com.cointrail.testing

import com.cointrail.data.sync.SyncController
import com.cointrail.data.sync.SyncJournal
import com.cointrail.data.sync.SyncJournalMerge
import com.cointrail.data.sync.SyncLocalStore
import com.cointrail.data.sync.SyncOutcome
import com.cointrail.data.sync.SyncRemoteStore
import com.cointrail.data.sync.SyncSettings
import com.cointrail.data.sync.SyncStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime

/** An in-memory local namespace whose whole state is a [SyncJournal]. */
class FakeSyncLocalStore(initial: SyncJournal = SyncJournal.EMPTY) : SyncLocalStore {

    var journal: SyncJournal = initial
        private set

    var mergeCount: Int = 0
        private set

    override suspend fun mergeRemote(remote: SyncJournal): SyncJournal {
        mergeCount++
        journal = SyncJournalMerge.merge(journal, remote)
        return journal
    }
}

/** A scripted remote journal store that can fail on read. */
class FakeSyncRemoteStore(content: String? = null) : SyncRemoteStore {

    var content: String? = content
        private set

    var writes: Int = 0
        private set

    var onRead: (() -> Unit)? = null
    var readError: Throwable? = null

    override suspend fun readJournal(): String? {
        onRead?.invoke()
        readError?.let { throw it }
        return content
    }

    override suspend fun writeJournal(content: String) {
        this.content = content
        writes++
    }
}

/** In-memory [SyncSettings]. */
class FakeSyncSettings(initial: LocalDateTime? = null) : SyncSettings {

    private val state = MutableStateFlow(initial)

    var markCount: Int = 0
        private set

    fun lastSyncedAt(): LocalDateTime? = state.value

    override fun observeLastSyncedAt(): Flow<LocalDateTime?> = state

    override suspend fun markSyncedAt(at: LocalDateTime) {
        state.value = at
        markCount++
    }
}

/** A scripted [SyncController] for the settings-viewmodel tests. */
class FakeSyncController : SyncController {

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status = _status.asStateFlow()

    override val lastSyncedAt: Flow<LocalDateTime?> = MutableStateFlow(null).asStateFlow()

    var outcome: SyncOutcome = SyncOutcome.Done
    var syncCount: Int = 0
        private set

    override suspend fun syncNow(): SyncOutcome {
        syncCount++
        return outcome
    }
}
