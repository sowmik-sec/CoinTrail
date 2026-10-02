package com.cointrail.domain.sync

import java.time.LocalDateTime

/**
 * Last-write-wins reconciliation of two versions of the same record set (SPEC §7).
 *
 * Sync exchanges full-row journals, so both sides run this merge and must arrive at byte-identical
 * results. The winner for a key is, in order: the greater [updatedAt]; then a tombstone over a live
 * row (so an out-of-date live copy can never resurrect a deletion); then the greater [canonical]
 * form, which is symmetric and gives a stable tie-break for genuinely simultaneous edits.
 *
 * The result is the union of both sides: records present on only one side survive untouched, which
 * is what makes fully-offline changes safe — they queue in the local database and are never dropped.
 */
object SyncMerge {

    fun <T> byLastWrite(
        local: List<T>,
        remote: List<T>,
        key: (T) -> String,
        updatedAt: (T) -> LocalDateTime,
        isDeleted: (T) -> Boolean = { false },
        canonical: (T) -> String,
    ): List<T> {
        val merged = LinkedHashMap<String, T>()
        (local + remote).forEach { row ->
            val id = key(row)
            val existing = merged[id]
            merged[id] = if (existing == null) {
                row
            } else {
                newest(existing, row, updatedAt, isDeleted, canonical)
            }
        }
        return merged.values.toList()
    }

    private fun <T> newest(
        a: T,
        b: T,
        updatedAt: (T) -> LocalDateTime,
        isDeleted: (T) -> Boolean,
        canonical: (T) -> String,
    ): T {
        val byTime = updatedAt(a).compareTo(updatedAt(b))
        if (byTime != 0) return if (byTime > 0) a else b

        val aDeleted = isDeleted(a)
        val bDeleted = isDeleted(b)
        if (aDeleted != bDeleted) return if (aDeleted) a else b

        return if (canonical(a) >= canonical(b)) a else b
    }
}
