package com.cointrail.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "expenses", indices = [Index("occurredAt"), Index("updatedAt")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountPaisa: Long,
    val categoryId: String,
    val note: String?,
    val paymentMethodId: String?,
    val occurredAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)

@Entity(tableName = "categories", indices = [Index("updatedAt")])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val updatedAt: LocalDateTime,
)

@Entity(tableName = "payment_methods", indices = [Index("updatedAt")])
data class PaymentMethodEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isPreset: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val updatedAt: LocalDateTime,
)

@Entity(tableName = "budgets", indices = [Index(value = ["scopeKey"], unique = true), Index("updatedAt")])
data class BudgetEntity(
    @PrimaryKey val id: String,
    val scopeKey: String,
    val monthlyLimitPaisa: Long,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
) {
    companion object {
        const val OVERALL: String = "__overall__"
    }
}

@Entity(tableName = "recurring_series", indices = [Index("updatedAt")])
data class RecurringSeriesEntity(
    @PrimaryKey val id: String,
    val amountPaisa: Long,
    val categoryId: String,
    val note: String?,
    val paymentMethodId: String?,
    val dayOfMonth: Int,
    val startMonth: String,
    val lastGeneratedMonth: String?,
    val isPaused: Boolean,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
)
