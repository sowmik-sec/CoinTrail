package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.data.account.Account
import com.cointrail.domain.model.Expense
import com.cointrail.testing.FakeSyncLocalStore
import com.cointrail.testing.FakeSyncRemoteStore
import com.cointrail.testing.FakeSyncSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * End-to-end sync-cycle behaviour: push, pull, merge, tombstones, offline safety and status
 * reporting (SPEC §7, §11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncManagerTest {

    private val alice = Account.fromGoogle("alice@example.com", "Alice")
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 1, 10, 0)
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 2, 12, 0)

    private val account = MutableStateFlow<Account?>(alice)

    private fun expense(paisa: Long, updatedAt: LocalDateTime, deleted: Boolean = false) = Expense(
        id = "e1",
        amount = Money(paisa),
        categoryId = "preset-food",
        note = null,
        paymentMethodId = null,
        occurredAt = t0,
        createdAt = t0,
        updatedAt = updatedAt,
        deletedAt = if (deleted) updatedAt else null,
    )

    private fun journal(vararg expenses: Expense) = SyncJournal(expenses = expenses.toList())

    private fun manager(
        local: FakeSyncLocalStore,
        remote: FakeSyncRemoteStore,
        settings: FakeSyncSettings = FakeSyncSettings(),
    ) = SyncManager(
        account = account,
        localStore = { local },
        remoteStore = { remote },
        settings = settings,
        now = { now },
    )

    @Test
    fun `signed out reports unavailable and never touches the remote`() = runTest {
        account.value = null
        val remote = FakeSyncRemoteStore()

        val outcome = manager(FakeSyncLocalStore(), remote).syncNow()

        assertEquals(SyncOutcome.Failed(SyncManager.SIGN_IN_REQUIRED), outcome)
        assertEquals(0, remote.writes)
        assertTrue(remote.content == null)
    }

    @Test
    fun `pushes a local change to an empty remote`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t1)))
        val remote = FakeSyncRemoteStore()

        val outcome = manager(local, remote).syncNow()

        assertEquals(SyncOutcome.Done, outcome)
        assertEquals(1, remote.writes)
        assertEquals(journal(expense(100, t1)), SyncJournalCodec.decode(remote.content!!))
    }

    @Test
    fun `applies a newer remote change locally`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t0)))
        val remote = FakeSyncRemoteStore(SyncJournalCodec.encode(journal(expense(200, t1))))

        manager(local, remote).syncNow()

        assertEquals(Money(200), local.journal.expenses.single().amount)
    }

    @Test
    fun `a remote tombstone deletes locally and stays deleted on the next sync`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t0)))
        val remote = FakeSyncRemoteStore(SyncJournalCodec.encode(journal(expense(100, t1, deleted = true))))
        val manager = manager(local, remote)

        manager.syncNow()

        assertTrue(local.journal.expenses.single().isDeleted)
        assertEquals(1, local.journal.expenses.size)

        manager.syncNow()

        assertTrue(local.journal.expenses.single().isDeleted)
    }

    @Test
    fun `never loses an offline change that the remote does not have`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t1)))
        val remote = FakeSyncRemoteStore(SyncJournalCodec.encode(journal(expense(200, t0))))

        manager(local, remote).syncNow()

        val merged = SyncJournalCodec.decode(remote.content!!)
        assertEquals(1, merged.expenses.size)
        assertEquals(Money(100), merged.expenses.single().amount)
    }

    @Test
    fun `repeated syncs are idempotent`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t1)))
        val remote = FakeSyncRemoteStore()
        val manager = manager(local, remote)

        manager.syncNow()
        val afterFirst = remote.content
        manager.syncNow()

        assertEquals(afterFirst, remote.content)
        assertEquals(1, local.journal.expenses.size)
    }

    @Test
    fun `reports syncing while reading and synced afterwards`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t1)))
        val remote = FakeSyncRemoteStore()
        val manager = manager(local, remote)
        var statusWhileReading: SyncStatus? = null
        remote.onRead = { statusWhileReading = manager.status.value }

        val outcome = manager.syncNow()

        assertEquals(SyncStatus.Syncing, statusWhileReading)
        assertEquals(SyncOutcome.Done, outcome)
        assertEquals(SyncStatus.Synced(now), manager.status.value)
    }

    @Test
    fun `a read failure surfaces a failed status without losing local data`() = runTest {
        val local = FakeSyncLocalStore(journal(expense(100, t1)))
        val remote = FakeSyncRemoteStore().apply { readError = RuntimeException("offline") }
        val manager = manager(local, remote)

        val outcome = manager.syncNow()

        assertEquals(SyncOutcome.Failed("offline"), outcome)
        assertEquals(SyncStatus.Failed("offline"), manager.status.value)
        assertEquals(Money(100), local.journal.expenses.single().amount)
        assertEquals(0, remote.writes)
    }

    @Test
    fun `an authorization failure is handed back to the caller`() = runTest {
        val remote = FakeSyncRemoteStore().apply { readError = SyncAuthorizationRequired(null) }
        val manager = manager(FakeSyncLocalStore(), remote)

        val outcome = manager.syncNow()

        assertTrue(outcome is SyncOutcome.AuthorizationRequired)
        assertEquals(SyncStatus.Failed(SyncManager.DRIVE_PERMISSION_REQUIRED), manager.status.value)
    }

    @Test
    fun `records the time of the last successful sync`() = runTest {
        val settings = FakeSyncSettings()

        manager(FakeSyncLocalStore(), FakeSyncRemoteStore(), settings).syncNow()

        assertEquals(now, settings.lastSyncedAt())
        assertEquals(1, settings.markCount)
    }
}
