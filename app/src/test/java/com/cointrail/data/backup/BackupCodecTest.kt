package com.cointrail.data.backup

import com.cointrail.core.Money
import com.cointrail.data.sync.SyncJournal
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The backup file is CoinTrail's ten-year durability guarantee (SPEC §7, §8), so it must round-trip
 * every table exactly, carry a versioned envelope, keep money as integer paisa and ISO timestamps,
 * and refuse anything it does not understand (SPEC §11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupCodecTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 10, 30)
    private val exportedAt: LocalDateTime = LocalDateTime.of(2026, 10, 2, 21, 30)

    private fun fullJournal() = SyncJournal(
        expenses = listOf(
            Expense(
                id = "e1", amount = Money(125_050), categoryId = "preset-food", note = "lunch",
                paymentMethodId = "pm-cash", occurredAt = t0, createdAt = t0, updatedAt = t1,
            ),
            Expense(
                id = "e2", amount = Money(9_900), categoryId = "preset-transport", note = null,
                paymentMethodId = null, occurredAt = t0, createdAt = t0, updatedAt = t1, deletedAt = t1,
            ),
        ),
        categories = listOf(
            Category(id = "preset-food", name = "Food", isPreset = true, isHidden = false, sortOrder = 0, updatedAt = t0),
            Category(id = "cat-1", name = "Pets", isPreset = false, isHidden = true, sortOrder = 9, updatedAt = t1),
        ),
        paymentMethods = listOf(
            PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, isHidden = false, sortOrder = 1, updatedAt = t0),
        ),
        budgets = listOf(
            Budget(id = "b1", categoryId = null, monthlyLimit = Money(2_000_000), updatedAt = t0),
            Budget(id = "b2", categoryId = "preset-food", monthlyLimit = Money(500_000), updatedAt = t1, deletedAt = t1),
        ),
        recurring = listOf(
            RecurringSeries(
                id = "r1", amount = Money(150_000), categoryId = "preset-utilities", note = "internet",
                paymentMethodId = "pm-bkash", dayOfMonth = 5, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = YearMonth.of(2026, 10), isPaused = false, updatedAt = t1,
            ),
        ),
    )

    @Test
    fun `round-trips every table exactly and keeps the export time`() {
        val journal = fullJournal()

        val backup = BackupCodec.decode(BackupCodec.encode(journal, exportedAt))

        assertEquals(journal, backup.journal)
        assertEquals(exportedAt, backup.exportedAt)
    }

    @Test
    fun `empty journal round-trips`() {
        val backup = BackupCodec.decode(BackupCodec.encode(SyncJournal.EMPTY, exportedAt))

        assertEquals(SyncJournal.EMPTY, backup.journal)
    }

    @Test
    fun `stamps the format version and export time`() {
        val root = JSONObject(BackupCodec.encode(SyncJournal.EMPTY, exportedAt))

        assertEquals(BackupCodec.FORMAT, root.getString("format"))
        assertEquals(BackupCodec.VERSION, root.getInt("version"))
        assertEquals(exportedAt.toString(), root.getString("exportedAt"))
    }

    @Test
    fun `nests the five tables under a tables object`() {
        val tables = JSONObject(BackupCodec.encode(fullJournal(), exportedAt)).getJSONObject("tables")

        assertTrue(tables.has("expenses"))
        assertTrue(tables.has("categories"))
        assertTrue(tables.has("paymentMethods"))
        assertTrue(tables.has("budgets"))
        assertTrue(tables.has("recurring"))
    }

    @Test
    fun `stores money as integer paisa and timestamps as ISO`() {
        val tables = JSONObject(BackupCodec.encode(fullJournal(), exportedAt)).getJSONObject("tables")
        val expense = tables.getJSONArray("expenses").getJSONObject(0)

        assertEquals(125_050L, expense.getLong("amountPaisa"))
        assertTrue(expense.getString("occurredAt").startsWith("2026-10-01T09:00"))
    }

    @Test
    fun `a backup with no tables decodes to empty`() {
        val backup = BackupCodec.decode("""{"format":"${BackupCodec.FORMAT}","version":1}""")

        assertEquals(SyncJournal.EMPTY, backup.journal)
        assertNull(backup.exportedAt)
    }

    @Test
    fun `a malformed export time does not stop the backup from decoding`() {
        val root = JSONObject(BackupCodec.encode(fullJournal(), exportedAt))
            .put("exportedAt", "not-a-date")

        val backup = BackupCodec.decode(root.toString())

        assertEquals(fullJournal(), backup.journal)
        assertNull(backup.exportedAt)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val root = JSONObject(BackupCodec.encode(fullJournal(), exportedAt))
            .put("futureField", "ignored")
        root.getJSONObject("tables").getJSONArray("expenses").getJSONObject(0).put("futureColumn", 42)

        val backup = BackupCodec.decode(root.toString())

        assertEquals(fullJournal(), backup.journal)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a foreign document`() {
        BackupCodec.decode("""{"format":"something-else","version":1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a newer unsupported version`() {
        BackupCodec.decode("""{"format":"${BackupCodec.FORMAT}","version":99}""")
    }
}
