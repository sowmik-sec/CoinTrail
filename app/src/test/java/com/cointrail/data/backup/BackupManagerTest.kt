package com.cointrail.data.backup

import com.cointrail.core.Money
import com.cointrail.data.account.Account
import com.cointrail.data.sync.SyncAuthorizationRequired
import com.cointrail.data.sync.SyncJournal
import com.cointrail.domain.model.Expense
import com.cointrail.testing.FakeSyncLocalStore
import com.cointrail.testing.FakeSnapshotRemoteStore
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
 * Backup and restore behaviour on top of sync (SPEC §7, §11): snapshots contain the whole database,
 * restore merges idempotently so it is safe on fresh and existing devices, and JSON export/import
 * needs no account.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    private val alice = Account.fromGoogle("alice@example.com", "Alice")
    private val account = MutableStateFlow<Account?>(alice)
    private val local = FakeSyncLocalStore()
    private val remote = FakeSnapshotRemoteStore()
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 2, 21, 30)
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 10, 0)

    private fun manager(localStore: FakeSyncLocalStore = local, remoteStore: FakeSnapshotRemoteStore = remote) =
        BackupManager(
            account = account,
            localStore = { localStore },
            snapshotStore = { remoteStore },
            now = { now },
        )

    private fun expense(id: String, paisa: Long, updatedAt: LocalDateTime, deleted: Boolean = false) = Expense(
        id = id,
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

    private fun <T> assertSuccess(result: SnapshotResult<T>): T {
        assertTrue("expected Success but was $result", result is SnapshotResult.Success)
        return (result as SnapshotResult.Success).value
    }

    @Test
    fun `export then import into a fresh namespace is identical`() = runTest {
        local.mergeRemote(journal(expense("e1", 100, t1), expense("e2", 200, t1, deleted = true)))

        val json = assertSuccess(manager().exportJson())

        val target = FakeSyncLocalStore()
        assertSuccess(manager(localStore = target).importJson(json))

        assertEquals(local.journal, target.journal)
        assertEquals(2, target.journal.expenses.size)
        assertEquals(1, target.journal.expenses.count { it.isDeleted })
    }

    @Test
    fun `import merges last-write-wins and never clobbers newer local data`() = runTest {
        local.mergeRemote(journal(expense("e1", 500, t1)))
        val staleBackup = BackupCodec.encode(journal(expense("e1", 100, t0)), t0)

        assertSuccess(manager().importJson(staleBackup))

        assertEquals(Money(500), local.journal.expenses.single().amount)
    }

    @Test
    fun `importing the same file twice changes nothing the second time`() = runTest {
        val json = BackupCodec.encode(journal(expense("e1", 100, t1)), now)
        val target = FakeSyncLocalStore()
        val manager = manager(localStore = target)

        assertSuccess(manager.importJson(json))
        val afterFirst = target.journal
        assertSuccess(manager.importJson(json))

        assertEquals(afterFirst, target.journal)
    }

    @Test
    fun `a corrupt json file fails cleanly`() = runTest {
        val result = manager().importJson("{ not a backup }")

        assertTrue(result is SnapshotResult.Failed)
    }

    @Test
    fun `list snapshots returns the account's drive snapshots`() = runTest {
        remote.seed(content = "{}", at = t0)
        remote.seed(content = "{}", at = t1)

        val snapshots = assertSuccess(manager().listSnapshots())

        assertEquals(listOf(t1, t0), snapshots.map { it.createdAt })
    }

    @Test
    fun `list snapshots is empty rather than an error when signed out`() = runTest {
        account.value = null

        val result = manager().listSnapshots()

        assertEquals(SnapshotResult.Success(emptyList<SnapshotRef>()), result)
    }

    @Test
    fun `backup now writes a snapshot of the whole database`() = runTest {
        local.mergeRemote(journal(expense("e1", 100, t1)))

        val ref = assertSuccess(manager().backupNow())

        assertEquals(now, ref.createdAt)
        assertEquals(1, remote.writeCount)
        assertEquals(local.journal, BackupCodec.decode(remote.contentOf(ref.id)!!).journal)
    }

    @Test
    fun `backup now signs the user in first`() = runTest {
        account.value = null

        val result = manager().backupNow()

        assertEquals(SnapshotResult.Failed(BackupManager.SIGN_IN_REQUIRED), result)
        assertEquals(0, remote.writeCount)
    }

    @Test
    fun `restoring a snapshot merges it into local data idempotently`() = runTest {
        val ref = remote.seed(BackupCodec.encode(journal(expense("e1", 100, t1)), t1), at = t1)

        assertSuccess(manager().restoreSnapshot(ref.id))
        val afterFirst = local.journal
        assertSuccess(manager().restoreSnapshot(ref.id))

        assertEquals(Money(100), afterFirst.expenses.single().amount)
        assertEquals(afterFirst, local.journal)
    }

    @Test
    fun `restore does not resurrect a newer local tombstone`() = runTest {
        local.mergeRemote(journal(expense("e1", 100, t1, deleted = true)))
        val ref = remote.seed(BackupCodec.encode(journal(expense("e1", 100, t0)), t0), at = t0)

        assertSuccess(manager().restoreSnapshot(ref.id))

        assertTrue(local.journal.expenses.single().isDeleted)
    }

    @Test
    fun `drive consent is handed back to the caller`() = runTest {
        local.mergeRemote(journal(expense("e1", 100, t1)))
        remote.writeError = SyncAuthorizationRequired(null)

        val result = manager().backupNow()

        assertTrue(result is SnapshotResult.AuthorizationRequired)
    }

    @Test
    fun `a drive failure surfaces as a failed result without losing local data`() = runTest {
        local.mergeRemote(journal(expense("e1", 100, t1)))
        remote.writeError = RuntimeException("offline")

        val result = manager().backupNow()

        assertEquals(SnapshotResult.Failed("offline"), result)
        assertEquals(Money(100), local.journal.expenses.single().amount)
    }

    @Test
    fun `export fails cleanly when there is no local namespace`() = runTest {
        val empty = BackupManager(account, localStore = { null }, snapshotStore = { remote }, now = { now })

        val result = empty.exportJson()

        assertEquals(SnapshotResult.Failed(BackupManager.NO_LOCAL_DATA), result)
    }
}
