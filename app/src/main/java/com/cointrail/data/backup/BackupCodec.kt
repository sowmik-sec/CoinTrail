package com.cointrail.data.backup

import com.cointrail.data.sync.SyncJournal
import com.cointrail.data.sync.SyncJournalCodec
import org.json.JSONObject
import java.time.LocalDateTime

/**
 * A full-database backup as read from or written to a versioned JSON file (SPEC §7, Q34).
 *
 * [exportedAt] is informational only — restore merges the rows last-write-wins by their own
 * `updatedAt`, not by when the file was made, so an old backup can never clobber newer data.
 */
data class Backup(
    val journal: SyncJournal,
    val exportedAt: LocalDateTime?,
)

/**
 * Versioned JSON codec for a [Backup] (SPEC §7). The envelope is deliberately tiny and documented in
 * `docs/BACKUP_FORMAT.md`, so a future app — or a human with a text editor — can still read a
 * decade-old file:
 *
 * ```json
 * { "format": "cointrail-backup", "version": 1, "exportedAt": "<ISO>", "tables": { ... } }
 * ```
 *
 * Money is integer paisa and every timestamp is an ISO string. The per-table rows are encoded by
 * [SyncJournalCodec] so a backup and the live sync journal stay byte-for-byte in step. Unknown
 * fields are ignored and a version this build does not understand is refused rather than partially
 * applied.
 */
object BackupCodec {

    const val FORMAT: String = "cointrail-backup"
    const val VERSION: Int = 1

    fun encode(journal: SyncJournal, exportedAt: LocalDateTime): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt.toString())
        .put("tables", SyncJournalCodec.encodeTables(journal))
        .toString()

    fun decode(json: String): Backup {
        val root = JSONObject(json)
        val format = root.optString("format")
        require(format == FORMAT) { "Not a CoinTrail backup file (format=$format)" }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "Unsupported backup version: $version" }
        // exportedAt is informational: restore merges by each row's updatedAt, so a missing or
        // malformed value must never stop a valid backup from being restored.
        val exportedAt = root.optString("exportedAt").takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }
        val tables = root.optJSONObject("tables") ?: JSONObject()
        return Backup(journal = SyncJournalCodec.decodeTables(tables), exportedAt = exportedAt)
    }
}
