package com.cointrail.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

data class DailyTotalRow(val day: String, val totalPaisa: Long)

data class CategoryTotalRow(val categoryId: String, val totalPaisa: Long)

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(expenses: List<ExpenseEntity>)

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

    @Query("SELECT * FROM expenses WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<ExpenseEntity>

    @Query("UPDATE expenses SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("UPDATE expenses SET deletedAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun restore(id: String, now: LocalDateTime)
}

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<CategoryEntity>
}

@Dao
interface PaymentMethodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(paymentMethods: List<PaymentMethodEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(paymentMethod: PaymentMethodEntity)

    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<PaymentMethodEntity>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun byId(id: String): PaymentMethodEntity?

    @Query("SELECT COUNT(*) FROM payment_methods")
    suspend fun count(): Int

    @Query("SELECT * FROM payment_methods WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<PaymentMethodEntity>
}

@Dao
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE scopeKey = :scopeKey")
    suspend fun byScopeKey(scopeKey: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE deletedAt IS NULL ORDER BY scopeKey ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("UPDATE budgets SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("SELECT * FROM budgets WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<BudgetEntity>
}

@Dao
interface RecurringSeriesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(series: RecurringSeriesEntity)

    @Query("SELECT * FROM recurring_series WHERE id = :id")
    suspend fun byId(id: String): RecurringSeriesEntity?

    @Query("SELECT * FROM recurring_series WHERE deletedAt IS NULL ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<RecurringSeriesEntity>>

    @Query("UPDATE recurring_series SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: LocalDateTime)

    @Query("SELECT * FROM recurring_series WHERE updatedAt >= :since ORDER BY updatedAt ASC")
    suspend fun changesSince(since: LocalDateTime): List<RecurringSeriesEntity>
}
