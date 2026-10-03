package com.cointrail.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path

/**
 * The v1 → v2 upgrade must be invisible (issue 14, ticket 16): every existing budget simply becomes
 * its scope's default budget — same id, same limit, `month` null. A real v1 database file is built
 * by hand (DDL and identity hash straight from the exported `schemas/1.json`) and then opened
 * through [CoinTrailDatabase.create], so Room runs [CoinTrailDatabase.MIGRATION_1_2] and validates
 * the migrated schema against the exported `2.json` — a wrong migration fails the open.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BudgetMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val accountKey = "migration-test-account"
    private lateinit var dbFile: java.io.File

    @Before
    fun setUp() {
        dbFile = context.getDatabasePath(CoinTrailDatabase.databaseName(accountKey))
        dbFile.parentFile?.mkdirs()
        dbFile.delete()
    }

    @After
    fun tearDown() {
        listOf("", "-wal", "-shm").forEach { suffix -> java.io.File(dbFile.path + suffix).delete() }
    }

    @Test
    fun `v1 budgets migrate to default budgets keeping their ids and limits`() = runBlocking {
        buildV1Database(
            budgets = listOf(
                V1Budget(id = "b1", scopeKey = BudgetEntity.OVERALL, limitPaisa = 2_000_000),
                V1Budget(id = "b2", scopeKey = "preset-food", limitPaisa = 500_000),
            ),
        )

        // Opening through Room runs the migration and validates the migrated schema; a schema
        // mismatch would fail right here.
        val db = CoinTrailDatabase.create(context, accountKey)
        try {
            val rows = db.budgetDao().all().sortedBy { it.id }

            assertEquals(listOf("b1", "b2"), rows.map { it.id })
            rows.forEach { row ->
                assertNull("A migrated budget must be a default budget", row.month)
                assertNotNull(row.monthlyLimitPaisa)
            }
            assertEquals(BudgetEntity.OVERALL, rows[0].scopeKey)
            assertEquals(2_000_000L, rows[0].monthlyLimitPaisa)
            assertEquals("preset-food", rows[1].scopeKey)
            assertEquals(500_000L, rows[1].monthlyLimitPaisa)
        } finally {
            db.close()
        }
    }

    private data class V1Budget(val id: String, val scopeKey: String, val limitPaisa: Long)

    /** Builds a v1 database file exactly as Room v1 would have written it, every table included. */
    private fun buildV1Database(budgets: List<V1Budget>) {
        val schema = schemaV1().getJSONObject("database")
        val placeholder = "${'$'}{TABLE_NAME}"
        val entities = schema.getJSONArray("entities")
            .let { tables -> (0 until tables.length()).map { tables.getJSONObject(it) } }

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sql ->
            entities.forEach { entity ->
                val table = entity.getString("tableName")
                sql.execSQL(entity.getString("createSql").replace(placeholder, table))
                val indices = entity.optJSONArray("indices") ?: return@forEach
                (0 until indices.length()).map { indices.getJSONObject(it) }.forEach { index ->
                    sql.execSQL(index.getString("createSql").replace(placeholder, table))
                }
            }
            // The master table in the shape Room's generated code itself writes it.
            sql.execSQL("CREATE TABLE IF NOT EXISTS `room_master_table` (`id` INTEGER PRIMARY KEY, `identity_hash` TEXT)")
            sql.execSQL("INSERT OR REPLACE INTO `room_master_table` (`id`, `identity_hash`) VALUES (42, '${schema.getString("identityHash")}')")
            sql.version = 1
            budgets.forEach { budget ->
                sql.execSQL(
                    "INSERT INTO budgets (id, scopeKey, monthlyLimitPaisa, updatedAt, deletedAt) VALUES " +
                        "('${budget.id}', '${budget.scopeKey}', ${budget.limitPaisa}, " +
                        "'2026-10-01T09:00:00', NULL)",
                )
            }
        }
    }

    /** Reads the exported schema JSON; the Gradle test worker runs from the module directory. */
    private fun schemaV1(): JSONObject {
        val candidates = listOf(
            Path.of("schemas/com.cointrail.data.db.CoinTrailDatabase/1.json"),
            Path.of("app/schemas/com.cointrail.data.db.CoinTrailDatabase/1.json"),
        )
        val file = candidates.firstOrNull { Files.exists(it) }
            ?: error("Exported Room schema 1.json not found in ${candidates.map(Path::toString)}")
        return JSONObject(file.toFile().readText())
    }
}
