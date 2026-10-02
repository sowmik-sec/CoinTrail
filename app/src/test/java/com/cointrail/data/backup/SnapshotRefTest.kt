package com.cointrail.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/**
 * Snapshot file names encode their creation time so a Drive listing can be rendered without reading
 * every file (SPEC §7). Naming and parsing must be exact inverses, and foreign Drive files ignored.
 */
class SnapshotRefTest {

    private val at: LocalDateTime = LocalDateTime.of(2026, 10, 2, 21, 30, 5)

    @Test
    fun `name carries the prefix and a sortable timestamp`() {
        assertEquals("cointrail-snapshot-2026-10-02T21-30-05.json", SnapshotRef.nameFor(at))
    }

    @Test
    fun `parses its own name back to the same reference`() {
        val name = SnapshotRef.nameFor(at)

        val ref = SnapshotRef.fromDrive("file-1", name)

        assertEquals(SnapshotRef("file-1", name, at), ref)
    }

    @Test
    fun `ignores foreign files`() {
        assertNull(SnapshotRef.fromDrive("id", "cointrail-sync-journal.json"))
        assertNull(SnapshotRef.fromDrive("id", "holiday-photo.jpg"))
    }

    @Test
    fun `ignores a snapshot name with an unparseable timestamp`() {
        assertNull(SnapshotRef.fromDrive("id", "cointrail-snapshot-not-a-date.json"))
    }
}
