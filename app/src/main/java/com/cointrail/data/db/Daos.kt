package com.cointrail.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

data class DailyTotalRow(val day: String, val totalPaisa: Long)

data class CategoryTotalRow(val categoryId: String, val totalPaisa: Long)

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT * FROM expenses")
    suspend fun all(): List<ExpenseEntity>

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()

    /** Atomically makes the table exactly [expenses]; used to apply a merged sync journal. */
    @Transaction
    suspend fun replaceAll(expenses: List<ExpenseEntity>) {
        deleteAll()
        upsertAll(expenses)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun byId(id: String): ExpenseEntity?

    @Query("SELECT * FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to ORDER BY occurredAt DESC")
    fun observeBetween(from: LocalDateTime, to: LocalDateTime): Flow<List<ExpenseEntity>>

    @Query("SELECT COALESCE(SUM(amountPaisa), 0) FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to")
    fun observeTotalBetween(from: LocalDateTime, to: LocalDateTime): Flow<Long>

    @Query("SELECT COUNT(*) FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to")
    suspend fun countBetween(from: LocalDateTime, to: LocalDateTime): Int

    @Query("SELECT substr(occurredAt, 1, 10) AS day, SUM(amountPaisa) AS totalPaisa FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to GROUP BY day ORDER BY day ASC")
    fun observeDailyTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<DailyTotalRow>>

    @Query("SELECT categoryId, SUM(amountPaisa) AS totalPaisa FROM expenses WHERE deletedAt IS NULL AND occurredAt >= :from AND occurredAt < :to GROUP BY categoryId ORDER BY totalPaisa DESC")
    fun observeCategoryTotals(from: LocalDateTime, to: LocalDateTime): Flow<List<CategoryTotalRow>>

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("UPDATE expenses SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: LocalDateTime)
}

@Dao
interface CatalogDao {

    /** The categories half: one table's queries; the payment-methods half below mirrors them. */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun categoryById(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int

    @Transaction
    suspend fun replaceAllCategories(categories: List<CategoryEntity>) {
        deleteAllCategories()
        upsertAllCategories(categories)
    }

    /** The payment-methods half. */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllPaymentMethods(paymentMethods: List<CatalogItemEntity>)

    @Query("SELECT * FROM payment_methods")
    suspend fun allPaymentMethods(): List<CatalogItemEntity>

    @Query("DELETE FROM payment_methods")
    suspend fun deleteAllPaymentMethods()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPaymentMethod(paymentMethod: CatalogItemEntity)

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, name ASC")
    fun observeAllPaymentMethods(): Flow<List<CatalogItemEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun paymentMethodById(id: String): CatalogItemEntity?

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun paymentMethodCount(): Int

    @Transaction
    suspend fun replaceAllPaymentMethods(paymentMethods: List<CatalogItemEntity>) {
        deleteAllPaymentMethods()
        upsertAllPaymentMethods(paymentMethods)
    }
}

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(budgets: List<BudgetEntity>)

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    /** Atomically makes the table exactly [budgets]; used to apply a merged sync journal. */
    @Transaction
    suspend fun replaceAll(budgets: List<BudgetEntity>) {
        deleteAll()
        upsertAll(budgets)
    }

    @Query("SELECT * FROM budgets WHERE scopeKey = :scopeKey AND month IS :month")
    suspend fun byScopeAndMonth(scopeKey: String, month: String?): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY scopeKey ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("UPDATE budgets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)
}

@Dao
interface RecurringSeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: RecurringSeriesEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(series: List<RecurringSeriesEntity>)

    @Query("SELECT * FROM recurring_series")
    suspend fun all(): List<RecurringSeriesEntity>

    @Query("DELETE FROM recurring_series")
    suspend fun deleteAll()

    /** Atomically makes the table exactly [series]; used to apply a merged sync journal. */
    @Transaction
    suspend fun replaceAll(series: List<RecurringSeriesEntity>) {
        deleteAll()
        upsertAll(series)
    }

    @Query("SELECT * FROM recurring_series WHERE id = :id")
    suspend fun byId(id: String): RecurringSeriesEntity?

    @Query("SELECT * FROM recurring_series WHERE deletedAt IS NULL ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringSeriesEntity>>

    @Query("SELECT * FROM recurring_series WHERE deletedAt IS NULL ORDER BY dayOfMonth ASC")
    suspend fun active(): List<RecurringSeriesEntity>

    @Query("UPDATE recurring_series SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)
}
