package com.cointrail.data.backup

import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.account.Account
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.sync.RoomSyncLocalStore
import com.cointrail.data.sync.SyncJournal
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The ten-year durability guarantee, exercised against real Room databases: a JSON backup exported
 * from one device must import into a fresh device with byte-identical data, and must not damage an
 * existing device whose rows are newer (SPEC §7, §11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest {

    private lateinit var sourceDb: CoinTrailDatabase
    private lateinit var targetDb: CoinTrailDatabase
    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 10, 0)
    private val now: LocalDateTime = LocalDateTime.of(2026, 10, 2, 21, 30)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        sourceDb = CoinTrailDatabase.createInMemory(context)
        targetDb = CoinTrailDatabase.createInMemory(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    private fun managerFor(store: RoomSyncLocalStore) = BackupManager(
        account = MutableStateFlow<Account?>(null),
        localStore = { store },
        snapshotStore = { null },
        now = { now },
    )

    private fun expense(id: String, paisa: Long, updatedAt: LocalDateTime, deleted: Boolean = false) = Expense(
        id = id, amount = Money(paisa), categoryId = "preset-food", note = "n", paymentMethodId = "pm-cash",
        occurredAt = t0, createdAt = t0, updatedAt = updatedAt, deletedAt = if (deleted) updatedAt else null,
    )

    private fun sampleJournal() = SyncJournal(
        expenses = listOf(expense("e1", 125_050, t1), expense("e2", 9_900, t1, deleted = true)),
        categories = listOf(
            Category(id = "preset-food", name = "Food", isPreset = true, isHidden = false, sortOrder = 0, updatedAt = t0),
            Category(id = "cat-1", name = "Pets", isPreset = false, isHidden = true, sortOrder = 9, updatedAt = t1),
        ),
        paymentMethods = listOf(
            PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, isHidden = false, sortOrder = 0, updatedAt = t0),
        ),
        budgets = listOf(
            Budget(id = "b1", categoryId = null, monthlyLimit = Money(2_000_000), updatedAt = t0),
            Budget(id = "b2", categoryId = "preset-food", monthlyLimit = Money(500_000), updatedAt = t1, deletedAt = t1),
        ),
        recurring = listOf(
            RecurringSeries(
                id = "r1", amount = Money(150_000), categoryId = "preset-utilities", note = "internet",
                paymentMethodId = "pm-cash", dayOfMonth = 5, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = YearMonth.of(2026, 10), isPaused = false, updatedAt = t1,
            ),
        ),
    )

    @Test
    fun `exported json imports into a fresh device with identical data`() = runBlocking {
        val source = RoomSyncLocalStore(sourceDb).apply { mergeRemote(sampleJournal()) }
        val json = managerFor(source).exportJson() as SnapshotResult.Success

        assertTrue(json.value.contains("\"format\":\"${BackupCodec.FORMAT}\""))

        val target = RoomSyncLocalStore(targetDb)
        val imported = managerFor(target).importJson(json.value)
        assertTrue(imported is SnapshotResult.Success)

        assertEquals(source.read().sorted(), target.read().sorted())
    }

    @Test
    fun `import preserves newer data already on the device`() = runBlocking {
        val source = RoomSyncLocalStore(sourceDb).apply { mergeRemote(SyncJournal(expenses = listOf(expense("e1", 100, t0)))) }
        val json = (managerFor(source).exportJson() as SnapshotResult.Success).value

        val target = RoomSyncLocalStore(targetDb).apply { mergeRemote(SyncJournal(expenses = listOf(expense("e1", 500, t1)))) }
        assertTrue(managerFor(target).importJson(json) is SnapshotResult.Success)

        assertEquals(Money(500), target.read().expenses.single().amount)
    }

    private fun SyncJournal.sorted(): SyncJournal = SyncJournal(
        expenses = expenses.sortedBy { it.id },
        categories = categories.sortedBy { it.id },
        paymentMethods = paymentMethods.sortedBy { it.id },
        budgets = budgets.sortedBy { it.id },
        recurring = recurring.sortedBy { it.id },
    )
}
