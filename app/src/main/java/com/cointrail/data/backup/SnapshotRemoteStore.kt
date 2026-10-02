package com.cointrail.data.backup

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * A timestamped full backup stored in the user's Drive app-data folder (SPEC §7). The creation time
 * is encoded in the file name so a snapshot list can be rendered without reading every file body.
 */
data class SnapshotRef(
    val id: String,
    val name: String,
    val createdAt: LocalDateTime,
) {
    companion object {
        const val PREFIX: String = "cointrail-snapshot-"

        private const val SUFFIX = ".json"
        private val NAME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")

        /** The Drive file name for the snapshot taken at [at]; sortable and human-readable. */
        fun nameFor(at: LocalDateTime): String = PREFIX + NAME_FORMAT.format(at) + SUFFIX

        /** Rebuilds a [SnapshotRef] from a Drive file, or null if the name is not one of ours. */
        fun fromDrive(id: String, name: String): SnapshotRef? {
            if (!name.startsWith(PREFIX) || !name.endsWith(SUFFIX)) return null
            val raw = name.removePrefix(PREFIX).removeSuffix(SUFFIX)
            val createdAt = runCatching { LocalDateTime.parse(raw, NAME_FORMAT) }.getOrNull() ?: return null
            return SnapshotRef(id = id, name = name, createdAt = createdAt)
        }
    }
}

/**
 * The snapshots (timestamped full backups) in one account's Drive app folder (SPEC §7). This is the
 * transport only; restoring a snapshot is a last-write-wins merge performed by [BackupManager].
 */
interface SnapshotRemoteStore {

    /** Every snapshot, newest first. */
    suspend fun list(): List<SnapshotRef>

    /** The backup JSON body of the snapshot with [id]. */
    suspend fun read(id: String): String

    /** Writes a new snapshot taken at [at] and returns its reference. */
    suspend fun write(content: String, at: LocalDateTime): SnapshotRef
}
