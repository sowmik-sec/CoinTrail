package com.cointrail.ui.backup

import com.cointrail.data.account.Account
import com.cointrail.data.backup.SnapshotRef
import com.cointrail.data.backup.SnapshotResult
import com.cointrail.testing.FakeBackupController
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val alice = Account.fromGoogle("alice@example.com", "Alice")
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 9, 0)

    private fun snapshot(at: LocalDateTime) =
        SnapshotRef(id = "s-${at}", name = SnapshotRef.nameFor(at), createdAt = at)

    private fun viewModel(
        controller: FakeBackupController = FakeBackupController(),
        account: MutableStateFlow<Account?> = MutableStateFlow(alice),
    ) = BackupViewModel(
        backup = controller,
        account = account,
        now = { t1 },
    )

    @Test
    fun `loads the snapshot list on open`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply {
                listResult = SnapshotResult.Success(listOf(snapshot(t1), snapshot(t0)))
            }

            val vm = viewModel(controller)

            assertEquals(1, controller.listCount)
            assertEquals(listOf(t1, t0), vm.state.value.snapshots.map { it.createdAt })
            assertTrue(vm.state.value.signedIn)
        }

    @Test
    fun `reports signed out when there is no account`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel(account = MutableStateFlow(null))

            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `backup now creates a snapshot and reloads the list`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController()
            val vm = viewModel(controller)
            val listsBefore = controller.listCount

            vm.backupNow()

            assertEquals(1, controller.backupCount)
            assertEquals("Backup created", vm.state.value.message)
            assertEquals(listsBefore + 1, controller.listCount)
        }

    @Test
    fun `backup failure surfaces a message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply {
                backupResult = SnapshotResult.Failed("offline")
            }
            val vm = viewModel(controller)

            vm.backupNow()

            assertEquals("offline", vm.state.value.message)
        }

    @Test
    fun `a granted drive consent retries the backup`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply {
                backupResult = SnapshotResult.AuthorizationRequired(null)
            }
            val vm = viewModel(controller)

            vm.backupNow()
            assertEquals(1, controller.backupCount)

            vm.onDriveAuthorizationResult(granted = true)

            assertEquals(2, controller.backupCount)
        }

    @Test
    fun `a denied drive consent does not retry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply {
                backupResult = SnapshotResult.AuthorizationRequired(null)
            }
            val vm = viewModel(controller)

            vm.backupNow()
            vm.onDriveAuthorizationResult(granted = false)

            assertEquals(1, controller.backupCount)
        }

    @Test
    fun `restore reports success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController()
            val vm = viewModel(controller)

            vm.restore("snapshot-7")

            assertEquals(1, controller.restoreCount)
            assertEquals("snapshot-7", controller.lastRestoredId)
            assertEquals("Restored", vm.state.value.message)
        }

    @Test
    fun `import reports success`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController()
            val vm = viewModel(controller)

            vm.import("""{"format":"cointrail-backup"}""")

            assertEquals(1, controller.importCount)
            assertEquals("Imported", vm.state.value.message)
        }

    @Test
    fun `build export json returns the document`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply {
                exportResult = SnapshotResult.Success("""{"format":"cointrail-backup"}""")
            }

            val json = viewModel(controller).buildExportJson()

            assertEquals("""{"format":"cointrail-backup"}""", json)
        }

    @Test
    fun `dismissing the message clears it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val controller = FakeBackupController().apply { backupResult = SnapshotResult.Failed("offline") }
            val vm = viewModel(controller)
            vm.backupNow()

            vm.dismissMessage()

            assertNull(vm.state.value.message)
        }

    @Test
    fun `export file name includes the date`() =
        runTest(mainDispatcherRule.testDispatcher) {
            assertEquals("cointrail-backup-2026-10-02.json", viewModel().exportFileName())
        }
}
