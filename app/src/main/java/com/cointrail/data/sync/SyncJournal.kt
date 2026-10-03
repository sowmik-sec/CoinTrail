package com.cointrail.data.sync

import com.cointrail.core.Money
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import com.cointrail.domain.sync.SyncMerge
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.YearMonth

/**
 * The full row state of every syncable table, tombstones included (SPEC §7). Sync exchanges the
 * whole journal and merges it record by record, so a device that was offline for days simply pushes
 * everything it has and loses nothing.
 */
data class SyncJournal(
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val recurring: List<RecurringSeries> = emptyList(),
) {
    companion object {
        val EMPTY: SyncJournal = SyncJournal()
    }
}

/**
 * Reconciles two journals with last-write-wins per record (SPEC §7). Expense, category, payment
 * method and recurring rows use their id; budgets use (scope, month), because a budget's identity
 * is the scope and month it governs, not the generated row id — so two months' overrides for one
 * scope never annihilate each other.
 */
object SyncJournalMerge {

    fun merge(local: SyncJournal, remote: SyncJournal): SyncJournal = SyncJournal(
        expenses = SyncMerge.byLastWrite(
            local.expenses, remote.expenses, key = { it.id }, updatedAt = { it.updatedAt },
            isDeleted = { it.isDeleted }, canonical = { it.toString() },
        ),
        categories = SyncMerge.byLastWrite(
            local.categories, remote.categories, key = { it.id }, updatedAt = { it.updatedAt },
            canonical = { it.toString() },
        ),
        paymentMethods = SyncMerge.byLastWrite(
            local.paymentMethods, remote.paymentMethods, key = { it.id }, updatedAt = { it.updatedAt },
            canonical = { it.toString() },
        ),
        budgets = SyncMerge.byLastWrite(
            local.budgets, remote.budgets, key = { it.mergeKey() }, updatedAt = { it.updatedAt },
            isDeleted = { it.deletedAt != null }, canonical = { it.toString() },
        ),
        recurring = SyncMerge.byLastWrite(
            local.recurring, remote.recurring, key = { it.id }, updatedAt = { it.updatedAt },
            isDeleted = { it.deletedAt != null }, canonical = { it.toString() },
        ),
    )

    private fun Budget.mergeKey(): String = "$scopeKey|${month?.toString().orEmpty()}"
}

/**
 * Versioned JSON codec for [SyncJournal] (SPEC §7). Timestamps are ISO strings and money is integer
 * paisa, so the format stays human-readable and forward-migratable. Decoding ignores unknown fields
 * and refuses a version it does not understand rather than silently dropping rows.
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
     * The five tables as one JSON object, without any envelope. Shared with [BackupCodec] so the
     * backup file and the sync journal encode every row identically (SPEC §7).
     */
    internal fun encodeTables(journal: SyncJournal): JSONObject = JSONObject()
        .put("expenses", JSONArray(journal.expenses.map(::encodeExpense)))
        .put("categories", JSONArray(journal.categories.map(::encodeCategory)))
        .put("paymentMethods", JSONArray(journal.paymentMethods.map(::encodePaymentMethod)))
        .put("budgets", JSONArray(journal.budgets.map(::encodeBudget)))
        .put("recurring", JSONArray(journal.recurring.map(::encodeRecurring)))

    /** Reads the five tables from a JSON object, ignoring unknown tables and defaulting missing ones. */
    internal fun decodeTables(tables: JSONObject): SyncJournal = SyncJournal(
        expenses = tables.rows("expenses", ::decodeExpense),
        categories = tables.rows("categories", ::decodeCategory),
        paymentMethods = tables.rows("paymentMethods", ::decodePaymentMethod),
        budgets = tables.rows("budgets", ::decodeBudget),
        recurring = tables.rows("recurring", ::decodeRecurring),
    )

    private fun encodeExpense(expense: Expense): JSONObject = JSONObject()
        .put("id", expense.id)
        .put("amountPaisa", expense.amount.paisa)
        .put("categoryId", expense.categoryId)
        .put("note", expense.note ?: JSONObject.NULL)
        .put("paymentMethodId", expense.paymentMethodId ?: JSONObject.NULL)
        .put("occurredAt", expense.occurredAt.toString())
        .put("createdAt", expense.createdAt.toString())
        .put("updatedAt", expense.updatedAt.toString())
        .put("deletedAt", expense.deletedAt?.toString() ?: JSONObject.NULL)

    private fun decodeExpense(row: JSONObject): Expense = Expense(
        id = row.getString("id"),
        amount = Money(row.getLong("amountPaisa")),
        categoryId = row.getString("categoryId"),
        note = row.nullableString("note"),
        paymentMethodId = row.nullableString("paymentMethodId"),
        occurredAt = LocalDateTime.parse(row.getString("occurredAt")),
        createdAt = LocalDateTime.parse(row.getString("createdAt")),
        updatedAt = LocalDateTime.parse(row.getString("updatedAt")),
        deletedAt = row.nullableString("deletedAt")?.let(LocalDateTime::parse),
    )

    private fun encodeCategory(category: Category): JSONObject = JSONObject()
        .put("id", category.id)
        .put("name", category.name)
        .put("isPreset", category.isPreset)
        .put("isHidden", category.isHidden)
        .put("sortOrder", category.sortOrder)
        .put("updatedAt", category.updatedAt.toString())

    private fun decodeCategory(row: JSONObject): Category = Category(
        id = row.getString("id"),
        name = row.getString("name"),
        isPreset = row.getBoolean("isPreset"),
        isHidden = row.getBoolean("isHidden"),
        sortOrder = row.getInt("sortOrder"),
        updatedAt = LocalDateTime.parse(row.getString("updatedAt")),
    )

    private fun encodePaymentMethod(method: PaymentMethod): JSONObject = JSONObject()
        .put("id", method.id)
        .put("name", method.name)
        .put("isPreset", method.isPreset)
        .put("isHidden", method.isHidden)
        .put("sortOrder", method.sortOrder)
        .put("updatedAt", method.updatedAt.toString())

    private fun decodePaymentMethod(row: JSONObject): PaymentMethod = PaymentMethod(
        id = row.getString("id"),
        name = row.getString("name"),
        isPreset = row.getBoolean("isPreset"),
        isHidden = row.getBoolean("isHidden"),
        sortOrder = row.getInt("sortOrder"),
        updatedAt = LocalDateTime.parse(row.getString("updatedAt")),
    )

    private fun encodeBudget(budget: Budget): JSONObject = JSONObject()
        .put("id", budget.id)
        .put("categoryId", budget.categoryId ?: JSONObject.NULL)
        .put("month", budget.month?.toString() ?: JSONObject.NULL)
        .put("monthlyLimitPaisa", budget.monthlyLimit?.paisa ?: JSONObject.NULL)
        .put("updatedAt", budget.updatedAt.toString())
        .put("deletedAt", budget.deletedAt?.toString() ?: JSONObject.NULL)

    private fun decodeBudget(row: JSONObject): Budget = Budget(
        id = row.getString("id"),
        categoryId = row.nullableString("categoryId"),
        month = row.nullableString("month")?.let(YearMonth::parse),
        monthlyLimit = if (row.isNull("monthlyLimitPaisa")) null else Money(row.getLong("monthlyLimitPaisa")),
        updatedAt = LocalDateTime.parse(row.getString("updatedAt")),
        deletedAt = row.nullableString("deletedAt")?.let(LocalDateTime::parse),
    )

    private fun encodeRecurring(series: RecurringSeries): JSONObject = JSONObject()
        .put("id", series.id)
        .put("amountPaisa", series.amount.paisa)
        .put("categoryId", series.categoryId)
        .put("note", series.note ?: JSONObject.NULL)
        .put("paymentMethodId", series.paymentMethodId ?: JSONObject.NULL)
        .put("dayOfMonth", series.dayOfMonth)
        .put("startMonth", series.startMonth.toString())
        .put("lastGeneratedMonth", series.lastGeneratedMonth?.toString() ?: JSONObject.NULL)
        .put("isPaused", series.isPaused)
        .put("updatedAt", series.updatedAt.toString())
        .put("deletedAt", series.deletedAt?.toString() ?: JSONObject.NULL)

    private fun decodeRecurring(row: JSONObject): RecurringSeries = RecurringSeries(
        id = row.getString("id"),
        amount = Money(row.getLong("amountPaisa")),
        categoryId = row.getString("categoryId"),
        note = row.nullableString("note"),
        paymentMethodId = row.nullableString("paymentMethodId"),
        dayOfMonth = row.getInt("dayOfMonth"),
        startMonth = YearMonth.parse(row.getString("startMonth")),
        lastGeneratedMonth = row.nullableString("lastGeneratedMonth")?.let(YearMonth::parse),
        isPaused = row.getBoolean("isPaused"),
        updatedAt = LocalDateTime.parse(row.getString("updatedAt")),
        deletedAt = row.nullableString("deletedAt")?.let(LocalDateTime::parse),
    )

    private fun <T> JSONObject.rows(name: String, decode: (JSONObject) -> T): List<T> {
        val array = optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).map { decode(array.getJSONObject(it)) }
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }
}
