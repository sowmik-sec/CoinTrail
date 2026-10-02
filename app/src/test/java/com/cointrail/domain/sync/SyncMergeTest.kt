package com.cointrail.domain.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * The merge is the durability-critical heart of sync (SPEC §7, §11): per record it must pick the
 * last write by [LocalDateTime] and be deterministic and symmetric, so two devices converge on the
 * same result no matter which order they exchange journals.
 */
class SyncMergeTest {

    private data class Row(
        val id: String,
        val updatedAt: LocalDateTime,
        val deleted: Boolean = false,
        val label: String = "",
    )

    private val t0: LocalDateTime = LocalDateTime.of(2026, 10, 1, 9, 0)
    private val t1: LocalDateTime = LocalDateTime.of(2026, 10, 1, 10, 0)
    private val t2: LocalDateTime = LocalDateTime.of(2026, 10, 1, 11, 0)

    private fun merge(local: List<Row>, remote: List<Row>): List<Row> =
        SyncMerge.byLastWrite(
            local = local,
            remote = remote,
            key = { it.id },
            updatedAt = { it.updatedAt },
            isDeleted = { it.deleted },
            canonical = { it.toString() },
        )

    @Test
    fun `no records returns empty`() {
        assertTrue(merge(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `a record present on only one side is kept`() {
        val merged = merge(listOf(Row("local-only", t0)), listOf(Row("remote-only", t0)))

        assertEquals(setOf("local-only", "remote-only"), merged.map { it.id }.toSet())
    }

    @Test
    fun `the version with the newer updatedAt wins`() {
        val older = Row("e", t0, label = "older")
        val newer = Row("e", t2, label = "newer")

        assertEquals("newer", merge(listOf(older), listOf(newer)).single().label)
        assertEquals("newer", merge(listOf(newer), listOf(older)).single().label)
    }

    @Test
    fun `a newer tombstone beats an older live row and never resurrects`() {
        val live = Row("e", t0, deleted = false)
        val tombstone = Row("e", t1, deleted = true)

        assertEquals(true, merge(listOf(live), listOf(tombstone)).single().deleted)
        assertEquals(true, merge(listOf(tombstone), listOf(live)).single().deleted)
    }

    @Test
    fun `a tie breaks toward the tombstone`() {
        val live = Row("e", t1, deleted = false)
        val tombstone = Row("e", t1, deleted = true)

        assertEquals(true, merge(listOf(live), listOf(tombstone)).single().deleted)
        assertEquals(true, merge(listOf(tombstone), listOf(live)).single().deleted)
    }

    @Test
    fun `a full tie is broken deterministically and symmetrically`() {
        val a = Row("e", t1, label = "apple")
        val b = Row("e", t1, label = "banana")

        val ab = merge(listOf(a), listOf(b)).single()
        val ba = merge(listOf(b), listOf(a)).single()

        assertEquals(ab, ba)
        assertEquals("banana", ab.label)
    }

    @Test
    fun `merging is symmetric for mixed records`() {
        val local = listOf(Row("a", t1), Row("b", t0), Row("d", t1, deleted = true))
        val remote = listOf(Row("a", t0), Row("c", t2), Row("d", t0))

        assertEquals(merge(local, remote).toSet(), merge(remote, local).toSet())
    }

    @Test
    fun `merging is idempotent`() {
        val local = listOf(Row("a", t1), Row("b", t0))
        val remote = listOf(Row("b", t2), Row("c", t0))
        val once = merge(local, remote)

        assertEquals(once.toSet(), merge(once, remote).toSet())
        assertEquals(once.toSet(), merge(once, once).toSet())
    }

    @Test
    fun `the winner is chosen per record independently`() {
        val merged = merge(
            local = listOf(Row("a", t2, label = "local-a"), Row("b", t0, label = "local-b")),
            remote = listOf(Row("a", t0, label = "remote-a"), Row("b", t2, label = "remote-b")),
        ).associateBy { it.id }

        assertEquals("local-a", merged.getValue("a").label)
        assertEquals("remote-b", merged.getValue("b").label)
    }
}
