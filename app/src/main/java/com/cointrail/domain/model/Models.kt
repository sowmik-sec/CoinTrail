package com.cointrail.domain.model

import com.cointrail.core.Money
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID

data class Expense(
    val id: String = UUID.randomUUID().toString(),
    val amount: Money,
    val categoryId: String,
    val note: String? = null,
    val paymentMethodId: String? = null,
    val occurredAt: LocalDateTime,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(amount > Money.ZERO) { "Expense amount must be positive" }
        require(categoryId.isNotBlank()) { "Expense must have a category" }
    }

    val isDeleted: Boolean get() = deletedAt != null
}

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPreset: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: LocalDateTime,
) {
    init {
        require(name.isNotBlank()) { "Category name must not be blank" }
    }
}

data class PaymentMethod(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isPreset: Boolean = false,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val updatedAt: LocalDateTime,
) {
    init {
        require(name.isNotBlank()) { "Payment method name must not be blank" }
    }
}

data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String?,
    val monthlyLimit: Money,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(monthlyLimit > Money.ZERO) { "Budget limit must be positive" }
    }
}

data class RecurringSeries(
    val id: String = UUID.randomUUID().toString(),
    val amount: Money,
    val categoryId: String,
    val note: String? = null,
    val paymentMethodId: String? = null,
    val dayOfMonth: Int,
    val startMonth: YearMonth,
    val lastGeneratedMonth: YearMonth? = null,
    val isPaused: Boolean = false,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        require(amount > Money.ZERO) { "Recurring amount must be positive" }
        require(categoryId.isNotBlank()) { "Recurring series must have a category" }
        require(dayOfMonth in 1..31) { "dayOfMonth must be in 1..31" }
    }
}

data class DailyTotal(val day: LocalDate, val total: Money)

data class CategoryTotal(val categoryId: String, val total: Money)
