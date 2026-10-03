package com.cointrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringSeriesEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CoinTrailDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringSeriesDao(): RecurringSeriesDao

    companion object {
        const val LOCAL_ACCOUNT_KEY: String = "local"

        /**
         * Schema v2 (per-month budgets, SPEC §5): every existing v1 budget becomes its scope's
         * **default budget** — same id, same limit, `month` null — so the upgrade is invisible.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets_v2` (" +
                        "`id` TEXT NOT NULL, `scopeKey` TEXT NOT NULL, `month` TEXT, " +
                        "`monthlyLimitPaisa` INTEGER, `updatedAt` TEXT NOT NULL, `deletedAt` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "INSERT INTO `budgets_v2` " +
                        "(`id`, `scopeKey`, `month`, `monthlyLimitPaisa`, `updatedAt`, `deletedAt`) " +
                        "SELECT `id`, `scopeKey`, NULL, `monthlyLimitPaisa`, `updatedAt`, `deletedAt` FROM `budgets`",
                )
                db.execSQL("DROP TABLE `budgets`")
                db.execSQL("ALTER TABLE `budgets_v2` RENAME TO `budgets`")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_scopeKey_month` " +
                        "ON `budgets` (`scopeKey`, `month`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budgets_updatedAt` ON `budgets` (`updatedAt`)",
                )
            }
        }

        fun create(context: Context, accountKey: String = LOCAL_ACCOUNT_KEY): CoinTrailDatabase =
            Room.databaseBuilder(context, CoinTrailDatabase::class.java, databaseName(accountKey))
                .addMigrations(MIGRATION_1_2)
                .build()

        fun createInMemory(context: Context): CoinTrailDatabase =
            Room.inMemoryDatabaseBuilder(context, CoinTrailDatabase::class.java)
                .allowMainThreadQueries()
                .build()

        fun databaseName(accountKey: String): String {
            val readable = accountKey.lowercase().filter { it.isLetterOrDigit() }.take(16).ifEmpty { "account" }
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(accountKey.toByteArray(Charsets.UTF_8))
                .take(6)
                .joinToString("") { "%02x".format(it) }
            return "cointrail-$readable-$hash.db"
        }
    }
}
