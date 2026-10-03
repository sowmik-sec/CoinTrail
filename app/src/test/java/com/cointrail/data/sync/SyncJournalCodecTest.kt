package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The journal is the on-the-wire sync format, so it must round-trip every table exactly, tombstone
 * and null included, and refuse versions it does not understand (SPEC §7, §11).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncJournalCodecTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 2, 10, 30)

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
            Budget(
                id = "b3", categoryId = "preset-food", month = YearMonth.of(2026, 11),
                monthlyLimit = Money(900_000), updatedAt = t1,
            ),
            Budget(
                id = "b4", categoryId = null, month = YearMonth.of(2026, 12),
                monthlyLimit = null, updatedAt = t1,
            ),
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
    fun `round-trips every table exactly`() {
        val journal = fullJournal()

        val decoded = SyncJournalCodec.decode(SyncJournalCodec.encode(journal))

        assertEquals(journal, decoded)
    }

    @Test
    fun `empty journal round-trips`() {
        val decoded = SyncJournalCodec.decode(SyncJournalCodec.encode(SyncJournal.EMPTY))

        assertEquals(SyncJournal.EMPTY, decoded)
    }

    @Test
    fun `stamps the format and version`() {
        val root = JSONObject(SyncJournalCodec.encode(SyncJournal.EMPTY))

        assertEquals(SyncJournalCodec.FORMAT, root.getString("format"))
        assertEquals(SyncJournalCodec.VERSION, root.getInt("version"))
    }

    @Test
    fun `stores money as integer paisa`() {
        val root = JSONObject(SyncJournalCodec.encode(fullJournal()))
        val expense = root.getJSONArray("expenses").getJSONObject(0)

        assertEquals(125_050L, expense.getLong("amountPaisa"))
        assertTrue(expense.getString("occurredAt").startsWith("2026-10-01T09:00"))
    }

    @Test
    fun `budget rows carry their month and a no-budget row carries a null limit`() {
        val root = JSONObject(SyncJournalCodec.encode(fullJournal()))
        val budgets = root.getJSONArray("budgets")

        assertTrue(budgets.getJSONObject(0).isNull("month"))
        assertEquals("2026-11", budgets.getJSONObject(2).getString("month"))
        assertEquals(900_000L, budgets.getJSONObject(2).getLong("monthlyLimitPaisa"))
        assertTrue(budgets.getJSONObject(3).isNull("monthlyLimitPaisa"))
    }

    @Test
    fun `a budget row without a month field reads as the default budget`() {
        // A file written before per-month budgets has no month key at all (Q49).
        val json = """
            {"format":"${SyncJournalCodec.FORMAT}","version":1,"budgets":[
                {"id":"b1","categoryId":null,"monthlyLimitPaisa":2000000,
                 "updatedAt":"2026-10-01T09:00:00","deletedAt":null}
            ]}
        """.trimIndent()

        val decoded = SyncJournalCodec.decode(json)

        assertEquals(
            listOf(Budget(id = "b1", categoryId = null, monthlyLimit = Money(2_000_000), updatedAt = t0)),
            decoded.budgets,
        )
    }

    @Test
    fun `a journal with no tables decodes to empty`() {
        val decoded = SyncJournalCodec.decode(
            """{"format":"${SyncJournalCodec.FORMAT}","version":1}"""
        )

        assertEquals(SyncJournal.EMPTY, decoded)
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val root = JSONObject(SyncJournalCodec.encode(fullJournal()))
            .put("futureField", "ignored")
        root.getJSONArray("expenses").getJSONObject(0).put("futureColumn", 42)

        val decoded = SyncJournalCodec.decode(root.toString())

        assertEquals(fullJournal(), decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a foreign document`() {
        SyncJournalCodec.decode("""{"format":"something-else","version":1}""")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects a newer unsupported version`() {
        SyncJournalCodec.decode("""{"format":"${SyncJournalCodec.FORMAT}","version":99}""")
    }
}
