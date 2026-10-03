package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * Budgets merge last-write-wins per (scope, month), not per row id (SPEC §7, Q49): two months'
 * overrides for one scope must never annihilate each other, and an explicit "no budget" row must
 * survive a merge distinctly from a tombstone — a tombstone means "override removed", never
 * "no budget".
 */
class SyncJournalMergeTest {

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 1, 10, 0)
    private val t2: LocalDateTime = LocalDateTime.of(2026, 10, 1, 11, 0)
    private val october: YearMonth = YearMonth.of(2026, 10)
    private val november: YearMonth = YearMonth.of(2026, 11)

    private fun budget(
        id: String,
        month: YearMonth?,
        paisa: Long?,
        updatedAt: LocalDateTime,
        categoryId: String? = "preset-food",
    ) = Budget(
        id = id,
        categoryId = categoryId,
        month = month,
        monthlyLimit = paisa?.let(::Money),
        updatedAt = updatedAt,
    )

    private fun merge(local: List<Budget>, remote: List<Budget>) =
        SyncJournalMerge.merge(SyncJournal(budgets = local), SyncJournal(budgets = remote)).budgets

    @Test
    fun `two months' overrides for one scope survive a merge`() {
        val merged = merge(
            local = listOf(budget("oct", october, 900_000, t1)),
            remote = listOf(budget("nov", november, 400_000, t0)),
        )

        assertEquals(
            setOf(october to 900_000L, november to 400_000L),
            merged.map { it.month to it.monthlyLimit!!.paisa }.toSet(),
        )
    }

    @Test
    fun `the newest write wins for the same scope and month`() {
        val local = listOf(budget("mine", october, 900_000, t1))
        val remote = listOf(budget("theirs", october, 100_000, t0))

        assertEquals("mine", merge(local, remote).single().id)
        assertEquals("mine", merge(remote, local).single().id)
    }

    @Test
    fun `a default budget and a month override merge independently`() {
        val merged = merge(
            local = listOf(budget("default", null, 500_000, t1)),
            remote = listOf(budget("eid-override", october, 900_000, t1)),
        )

        assertEquals(2, merged.size)
    }

    @Test
    fun `a no-budget row beats an older override for the same month`() {
        val noBudget = budget("quiet", october, null, t1)
        val override = budget("old", october, 900_000, t0)

        val merged = merge(listOf(noBudget), listOf(override))

        val winner = merged.single()
        assertNull(winner.monthlyLimit)
        assertEquals(october, winner.month)
    }

    @Test
    fun `a newer tombstone beats an override and a tombstone is not a no-budget row`() {
        val tombstone = budget("removed", october, 900_000, t1).copy(deletedAt = t1)
        val live = budget("old", october, 400_000, t0)

        val merged = merge(listOf(tombstone), listOf(live))

        val winner = merged.single()
        assertNotNull(winner.deletedAt)
        assertNotNull(winner.monthlyLimit)
    }

    @Test
    fun `a local-only tombstone and a remote month never collide`() {
        val merged = merge(
            local = listOf(budget("removed", october, 900_000, t1).copy(deletedAt = t1)),
            remote = listOf(budget("nov", november, 400_000, t0)),
        )

        assertEquals(2, merged.size)
        assertTrue(merged.single { it.month == october }.isDeleted)
        assertTrue(merged.single { it.month == november }.deletedAt == null)
    }
}
