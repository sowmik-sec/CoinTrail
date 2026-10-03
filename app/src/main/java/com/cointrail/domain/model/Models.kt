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

/**
 * One budget row for a scope (overall or a category): either the scope's **default budget**
 * ([month] null, the standing monthly limit) or a **budget override** for one month (SPEC §6.5).
 * An override carries either an explicit positive limit or a null limit — the explicit
 * "no budget for this month" state, which is distinct from a tombstone (an override removed).
 */
data class Budget(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String?,
    val month: YearMonth? = null,
    val monthlyLimit: Money?,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime? = null,
) {
    init {
        if (month == null) {
            require(monthlyLimit != null && monthlyLimit > Money.ZERO) {
                "A default budget must have a positive limit"
            }
        } else {
            require(monthlyLimit == null || monthlyLimit > Money.ZERO) {
                "A budget override's limit must be positive when present"
            }
        }
    }

    val isDeleted: Boolean get() = deletedAt != null

    /** True when this row is a scope's default budget rather than a month's override. */
    val isDefault: Boolean get() = month == null

    /** True when this row marks a month as explicitly having no budget (SPEC §6.5). */
    val isNoBudget: Boolean get() = month != null && monthlyLimit == null

    /** The scope this budget governs: a category, or [OVERALL_SCOPE] for the overall budget. */
    val scopeKey: String get() = categoryId ?: OVERALL_SCOPE

    companion object {
        /** The scope key of the overall (non-category) budget; stable across sync and storage. */
        const val OVERALL_SCOPE: String = "__overall__"
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
