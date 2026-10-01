package com.cointrail.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import java.security.MessageDigest

@Database(
    entities = [
        ExpenseEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringSeriesEntity::class,
    ],
    version = 1,
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

        fun create(context: Context, accountKey: String = LOCAL_ACCOUNT_KEY): CoinTrailDatabase =
            Room.databaseBuilder(context, CoinTrailDatabase::class.java, databaseName(accountKey)).build()

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
