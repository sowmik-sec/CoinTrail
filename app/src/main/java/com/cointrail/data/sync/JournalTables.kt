package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.repo.toCatalogItem
import com.cointrail.data.repo.toCatalogItemEntity
import com.cointrail.data.repo.toCategoryEntity
import com.cointrail.data.repo.toDomain
import com.cointrail.data.repo.toEntity
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.RecurringSeries
import com.cointrail.domain.sync.SyncMerge
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * One **journal table** (CONTEXT.md): a record type the sync journal carries in full so two devices
 * can reconcile (SPEC §7). A descriptor owns everything that would otherwise be re-enumerated per
 * table — the JSON key and row shape it publishes in journals and backup files, the key that decides
 * which of two rows is newer, and its read and write against the database — so the merge, the codec
 * and the local store iterate [JournalTables.all] instead of naming tables, and adding a table means
 * writing one descriptor. The table declarations the database framework requires stay written out
 * literally in [CoinTrailDatabase].
 */
class JournalTable<T>(
    /** The table's key, identical in the sync journal and in a backup file's tables object. */
    internal val name: String,
    /** The table's rows as [SyncJournal] carries them. */
    internal val rowsIn: (SyncJournal) -> List<T>,
    /** [rowsIn]'s inverse: the journal with this table's rows replaced. */
    internal val withRows: (SyncJournal, List<T>) -> SyncJournal,
    /** The table's rows, tombstones included, read from the database. */
    internal val readRows: suspend (CoinTrailDatabase) -> List<T>,
    /** Atomically replaces the table's contents in the database. */
    internal val writeRows: suspend (CoinTrailDatabase, List<T>) -> Unit,
    /** Decides which of two rows is a later edit of the same record (SPEC §7). */
    internal val mergeKey: (T) -> String,
    internal val updatedAt: (T) -> LocalDateTime,
    internal val encodeRow: (T) -> JSONObject,
    internal val decodeRow: (JSONObject) -> T,
    /** Only rows carrying a tombstone ever beat a live copy of the same record. */
    internal val isDeleted: (T) -> Boolean = { false },
) {

    /** Last-write-wins reconciliation of the table's local and remote rows (SPEC §7). */
    internal fun merged(local: List<T>, remote: List<T>): List<T> =
        SyncMerge.byLastWrite(
            local, remote, key = mergeKey, updatedAt = updatedAt,
            isDeleted = isDeleted, canonical = { it.toString() },
        )

    /** The journal with this table's freshly read database rows in place. */
    internal suspend fun readInto(journal: SyncJournal, database: CoinTrailDatabase): SyncJournal =
        withRows(journal, readRows(database))

    /** Replaces the table's contents in the database with the journal's rows. */
    internal suspend fun writeFrom(database: CoinTrailDatabase, journal: SyncJournal) {
        writeRows(database, rowsIn(journal))
    }

    /** Writes the table's rows under [name] into the tables object of a journal or backup. */
    internal fun appendedTo(tables: JSONObject, journal: SyncJournal): JSONObject {
        tables.put(name, JSONArray(rowsIn(journal).map(encodeRow)))
        return tables
    }

    /** The journal with this table's rows read back from [tables]; a missing table decodes empty. */
    internal fun rowsFrom(tables: JSONObject, into: SyncJournal): SyncJournal {
        val array = tables.optJSONArray(name) ?: return into
        return withRows(into, (0 until array.length()).map { decodeRow(array.getJSONObject(it)) })
    }
}

/**
 * Every journal table, in one place. The order is also the write order [RoomSyncLocalStore] uses.
 */
object JournalTables {

    val expenses: JournalTable<Expense> = JournalTable(
        name = "expenses",
        rowsIn = { it.expenses },
        withRows = { journal, rows -> journal.copy(expenses = rows) },
        readRows = { database -> database.expenseDao().all().map { it.toDomain() } },
        writeRows = { database, rows -> database.expenseDao().replaceAll(rows.map { it.toEntity() }) },
        mergeKey = { it.id },
        updatedAt = { it.updatedAt },
        encodeRow = ::encodeExpense,
        decodeRow = ::decodeExpense,
        isDeleted = { it.isDeleted },
    )

    // Both catalog tables carry CatalogItem rows under their own keys; their JSON row shape is the
    // same six fields, encoded once below.
    val categories: JournalTable<CatalogItem> = JournalTable(
        name = "categories",
        rowsIn = { it.categories },
        withRows = { journal, rows -> journal.copy(categories = rows) },
        readRows = { database -> database.catalogDao().allCategories().map { it.toCatalogItem() } },
        writeRows = { database, rows -> database.catalogDao().replaceAllCategories(rows.map { it.toCategoryEntity() }) },
        mergeKey = { it.id },
        updatedAt = { it.updatedAt },
        encodeRow = ::encodeCatalogRow,
        decodeRow = ::decodeCatalogRow,
    )

    val paymentMethods: JournalTable<CatalogItem> = JournalTable(
        name = "paymentMethods",
        rowsIn = { it.paymentMethods },
        withRows = { journal, rows -> journal.copy(paymentMethods = rows) },
        readRows = { database -> database.catalogDao().allPaymentMethods().map { it.toCatalogItem() } },
        writeRows = { database, rows -> database.catalogDao().replaceAllPaymentMethods(rows.map { it.toCatalogItemEntity() }) },
        mergeKey = { it.id },
        updatedAt = { it.updatedAt },
        encodeRow = ::encodeCatalogRow,
        decodeRow = ::decodeCatalogRow,
    )

    val budgets: JournalTable<Budget> = JournalTable(
        name = "budgets",
        rowsIn = { it.budgets },
        withRows = { journal, rows -> journal.copy(budgets = rows) },
        readRows = { database -> database.budgetDao().all().map { it.toDomain() } },
        writeRows = { database, rows -> database.budgetDao().replaceAll(rows.map { it.toEntity() }) },
        // A budget's identity is the scope and month it governs, not the generated row id — so two
        // months' overrides for one scope never annihilate each other (SPEC §7).
        mergeKey = { "${it.scopeKey}|${it.month?.toString().orEmpty()}" },
        updatedAt = { it.updatedAt },
        encodeRow = ::encodeBudget,
        decodeRow = ::decodeBudget,
        isDeleted = { it.deletedAt != null },
    )

    val recurring: JournalTable<RecurringSeries> = JournalTable(
        name = "recurring",
        rowsIn = { it.recurring },
        withRows = { journal, rows -> journal.copy(recurring = rows) },
        readRows = { database -> database.recurringSeriesDao().all().map { it.toDomain() } },
        writeRows = { database, rows -> database.recurringSeriesDao().replaceAll(rows.map { it.toEntity() }) },
        mergeKey = { it.id },
        updatedAt = { it.updatedAt },
        encodeRow = ::encodeRecurring,
        decodeRow = ::decodeRecurring,
        isDeleted = { it.deletedAt != null },
    )

    val all: List<JournalTable<*>> = listOf(expenses, categories, paymentMethods, budgets, recurring)

    /**
     * Merges two journals last-write-wins, every [JournalTable] once — [SyncJournalMerge]'s engine,
     * generic over the descriptors so no table is named here.
     */
    internal fun merged(local: SyncJournal, remote: SyncJournal): SyncJournal {
        var journal = SyncJournal.EMPTY
        for (table in all) journal = erased(table).run {
            withRows(journal, merged(rowsIn(local), rowsIn(remote)))
        }
        return journal
    }

    /** The tables object of a journal or backup file, every [JournalTable] once. */
    internal fun encodedTables(journal: SyncJournal): JSONObject =
        all.fold(JSONObject()) { tables, table -> erased(table).appendedTo(tables, journal) }

    /** The journal carried by a tables object, rows of unknown tables ignored, missing ones empty. */
    internal fun decodedTables(tables: JSONObject): SyncJournal =
        all.fold(SyncJournal.EMPTY) { journal, table -> erased(table).rowsFrom(tables, journal) }

    /** Reads every table's rows from the database, tombstones included. */
    internal suspend fun readAll(database: CoinTrailDatabase): SyncJournal =
        all.fold(SyncJournal.EMPTY) { journal, table -> erased(table).readInto(journal, database) }

    /** Replaces every table's contents in the database with the journal's rows. */
    internal suspend fun writeAll(database: CoinTrailDatabase, journal: SyncJournal) {
        for (table in all) erased(table).writeFrom(database, journal)
    }

    // A descriptor's row type appears only inside its own self-consistent lambdas, so re-typing one
    // against Any? cannot misbehave at runtime; this is the registry's single unchecked cast.
    @Suppress("UNCHECKED_CAST")
    private fun erased(table: JournalTable<*>): JournalTable<Any?> = table as JournalTable<Any?>
}

private fun encodeExpense(row: Expense): JSONObject = JSONObject()
    .put("id", row.id)
    .put("amountPaisa", row.amount.paisa)
    .put("categoryId", row.categoryId)
    .put("note", row.note ?: JSONObject.NULL)
    .put("paymentMethodId", row.paymentMethodId ?: JSONObject.NULL)
    .put("occurredAt", row.occurredAt.toString())
    .put("createdAt", row.createdAt.toString())
    .put("updatedAt", row.updatedAt.toString())
    .put("deletedAt", row.deletedAt?.toString() ?: JSONObject.NULL)

private fun decodeExpense(json: JSONObject): Expense = Expense(
    id = json.getString("id"),
    amount = Money(json.getLong("amountPaisa")),
    categoryId = json.getString("categoryId"),
    note = json.nullableString("note"),
    paymentMethodId = json.nullableString("paymentMethodId"),
    occurredAt = LocalDateTime.parse(json.getString("occurredAt")),
    createdAt = LocalDateTime.parse(json.getString("createdAt")),
    updatedAt = LocalDateTime.parse(json.getString("updatedAt")),
    deletedAt = json.nullableString("deletedAt")?.let(LocalDateTime::parse),
)

// One row shape for both catalog tables: the same fields the twin codecs wrote before, so existing
// journals and backup files decode unchanged.
private fun encodeCatalogRow(row: CatalogItem): JSONObject = JSONObject()
    .put("id", row.id)
    .put("name", row.name)
    .put("isPreset", row.isPreset)
    .put("isHidden", row.isHidden)
    .put("sortOrder", row.sortOrder)
    .put("updatedAt", row.updatedAt.toString())

private fun decodeCatalogRow(json: JSONObject): CatalogItem = CatalogItem(
    id = json.getString("id"),
    name = json.getString("name"),
    isPreset = json.getBoolean("isPreset"),
    isHidden = json.getBoolean("isHidden"),
    sortOrder = json.getInt("sortOrder"),
    updatedAt = LocalDateTime.parse(json.getString("updatedAt")),
)

private fun encodeBudget(row: Budget): JSONObject = JSONObject()
    .put("id", row.id)
    .put("categoryId", row.categoryId ?: JSONObject.NULL)
    .put("month", row.month?.toString() ?: JSONObject.NULL)
    .put("monthlyLimitPaisa", row.monthlyLimit?.paisa ?: JSONObject.NULL)
    .put("updatedAt", row.updatedAt.toString())
    .put("deletedAt", row.deletedAt?.toString() ?: JSONObject.NULL)

private fun decodeBudget(json: JSONObject): Budget = Budget(
    id = json.getString("id"),
    categoryId = json.nullableString("categoryId"),
    month = json.nullableString("month")?.let(YearMonth::parse),
    monthlyLimit = if (json.isNull("monthlyLimitPaisa")) null else Money(json.getLong("monthlyLimitPaisa")),
    updatedAt = LocalDateTime.parse(json.getString("updatedAt")),
    deletedAt = json.nullableString("deletedAt")?.let(LocalDateTime::parse),
)

private fun encodeRecurring(row: RecurringSeries): JSONObject = JSONObject()
    .put("id", row.id)
    .put("amountPaisa", row.amount.paisa)
    .put("categoryId", row.categoryId)
    .put("note", row.note ?: JSONObject.NULL)
    .put("paymentMethodId", row.paymentMethodId ?: JSONObject.NULL)
    .put("dayOfMonth", row.dayOfMonth)
    .put("startMonth", row.startMonth.toString())
    .put("lastGeneratedMonth", row.lastGeneratedMonth?.toString() ?: JSONObject.NULL)
    .put("isPaused", row.isPaused)
    .put("updatedAt", row.updatedAt.toString())
    .put("deletedAt", row.deletedAt?.toString() ?: JSONObject.NULL)

private fun decodeRecurring(json: JSONObject): RecurringSeries = RecurringSeries(
    id = json.getString("id"),
    amount = Money(json.getLong("amountPaisa")),
    categoryId = json.getString("categoryId"),
    note = json.nullableString("note"),
    paymentMethodId = json.nullableString("paymentMethodId"),
    dayOfMonth = json.getInt("dayOfMonth"),
    startMonth = YearMonth.parse(json.getString("startMonth")),
    lastGeneratedMonth = json.nullableString("lastGeneratedMonth")?.let(YearMonth::parse),
    isPaused = json.getBoolean("isPaused"),
    updatedAt = LocalDateTime.parse(json.getString("updatedAt")),
    deletedAt = json.nullableString("deletedAt")?.let(LocalDateTime::parse),
)

private fun JSONObject.nullableString(name: String): String? =
    if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
