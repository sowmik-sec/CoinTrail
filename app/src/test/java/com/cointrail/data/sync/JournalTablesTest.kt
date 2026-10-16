package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.RecurringSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The journal-table registry's completeness guard: a table added to [SyncJournal] cannot go
 * unnoticed, because a field with no descriptor — or two descriptors reading the same field — fails
 * here before any device's data can diverge (ticket 23).
 */
class JournalTablesTest {

    private val day = LocalDateTime.of(2026, 10, 5, 21, 30)

    /** One row per journal table, each journal carrying a single field's rows. */
    private val singleRowJournals = listOf(
        "expenses" to SyncJournal(
            expenses = listOf(
                Expense(
                    amount = Money(100),
                    categoryId = "preset-food",
                    occurredAt = day,
                    createdAt = day,
                    updatedAt = day,
                ),
            ),
        ),
        "categories" to SyncJournal(categories = listOf(CatalogItem(name = "Food", updatedAt = day))),
        "paymentMethods" to SyncJournal(paymentMethods = listOf(CatalogItem(name = "Cash", updatedAt = day))),
        "budgets" to SyncJournal(
            budgets = listOf(
                Budget(categoryId = null, month = YearMonth.of(2026, 10), monthlyLimit = Money(100), updatedAt = day),
            ),
        ),
        "recurring" to SyncJournal(
            recurring = listOf(
                RecurringSeries(
                    amount = Money(100),
                    categoryId = "preset-rent",
                    dayOfMonth = 1,
                    startMonth = YearMonth.of(2026, 10),
                    updatedAt = day,
                ),
            ),
        ),
    )

    @Test
    fun `every table the journal carries has exactly one descriptor`() {
        // The journal's fields are its tables; only the List-typed fields count (Java reflection
        // keeps the type honest without pulling in kotlin-reflect).
        val fields = SyncJournal::class.java.declaredFields
            .filter { List::class.java.isAssignableFrom(it.type) }
            .map { it.name }
            .sorted()

        // A field added to the journal type must come with a row below, so its descriptor's field
        // can be named here when the journal grows.
        assertEquals(fields, singleRowJournals.map { it.first }.sorted())

        val covered = JournalTables.all.map { table ->
            singleRowJournals
                .filter { (_, journal) -> table.rowsIn(journal).isNotEmpty() }
                .map { it.first }
        }

        // Each descriptor reads exactly one of those fields, and no two descriptors read the same one.
        assertEquals(fields, covered.map { it.single() }.sorted())
    }
}
