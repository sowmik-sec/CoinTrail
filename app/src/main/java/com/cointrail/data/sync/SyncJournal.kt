package com.cointrail.data.sync

import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.RecurringSeries
import org.json.JSONObject

/**
 * The full row state of every syncable table, tombstones included (SPEC §7). Sync exchanges the
 * whole journal and merges it record by record, so a device that was offline for days simply pushes
 * everything it has and loses nothing. Both catalog tables carry [CatalogItem] rows under their
 * own keys, so the journal and every backup file keep exactly the shape they always had.
 */
data class SyncJournal(
    val expenses: List<Expense> = emptyList(),
    val categories: List<CatalogItem> = emptyList(),
    val paymentMethods: List<CatalogItem> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<RecurringSeries> = emptyList(),
) {
    companion object {
        val EMPTY: SyncJournal = SyncJournal()
    }
}

/**
 * Reconciles two journals with last-write-wins per record (SPEC §7). Each [JournalTable] supplies
 * its own merge identity, so this file names no table: expense, category, payment method and
 * recurring rows reconcile by their id, budgets by (scope, month) — because a budget's identity is
 * the scope and month it governs, not the generated row id — so two months' overrides for one scope
 * never annihilate each other.
 */
object SyncJournalMerge {

    fun merge(local: SyncJournal, remote: SyncJournal): SyncJournal =
        JournalTables.merged(local, remote)
}

/**
 * Versioned JSON codec for [SyncJournal] (SPEC §7). Timestamps are ISO strings and money is integer
 * paisa, so the format stays human-readable and forward-migratable. Decoding ignores unknown fields
 * and refuses a version it does not understand rather than silently dropping rows. Each row's JSON
 * shape belongs to its [JournalTable]; this file owns only the envelope.
 */
object SyncJournalCodec {

    const val FORMAT: String = "cointrail-journal"
    const val VERSION: Int = 1

    fun encode(journal: SyncJournal): String {
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
        val tables = encodeTables(journal)
        tables.keys().forEach { key -> root.put(key, tables.get(key)) }
        return root.toString()
    }

    fun decode(json: String): SyncJournal {
        val root = JSONObject(json)
        val format = root.optString("format")
        require(format == FORMAT) { "Not a CoinTrail sync journal (format=$format)" }
        val version = root.optInt("version", 0)
        require(version in 1..VERSION) { "Unsupported sync journal version: $version" }
        return decodeTables(root)
    }

    /**
     * Every journal table as one JSON object, without any envelope. Shared with [BackupCodec] so the
     * backup file and the sync journal encode every row identically (SPEC §7).
     */
    internal fun encodeTables(journal: SyncJournal): JSONObject =
        JournalTables.encodedTables(journal)

    /** Reads every journal table from a JSON object, ignoring unknown tables and defaulting missing ones. */
    internal fun decodeTables(tables: JSONObject): SyncJournal =
        JournalTables.decodedTables(tables)
}
