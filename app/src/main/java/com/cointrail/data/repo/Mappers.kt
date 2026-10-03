package com.cointrail.data.repo

import com.cointrail.core.Money
import com.cointrail.data.db.BudgetEntity
import com.cointrail.data.db.CategoryEntity
import com.cointrail.data.db.ExpenseEntity
import com.cointrail.data.db.PaymentMethodEntity
import com.cointrail.data.db.RecurringSeriesEntity
import com.cointrail.domain.model.Budget
import com.cointrail.domain.model.Category
import com.cointrail.domain.model.Expense
import com.cointrail.domain.model.PaymentMethod
import com.cointrail.domain.model.RecurringSeries
import java.time.YearMonth

internal fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id,
    amount = Money(amountPaisa),
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id,
    amountPaisa = amount.paisa,
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun CategoryEntity.toDomain(): Category = Category(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun PaymentMethodEntity.toDomain(): PaymentMethod = PaymentMethod(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun PaymentMethod.toEntity(): PaymentMethodEntity = PaymentMethodEntity(
    id = id, name = name, isPreset = isPreset, isHidden = isHidden, sortOrder = sortOrder, updatedAt = updatedAt,
)

internal fun BudgetEntity.toDomain(): Budget = Budget(
    id = id,
    categoryId = if (scopeKey == BudgetEntity.OVERALL) null else scopeKey,
    month = month?.let(YearMonth::parse),
    monthlyLimit = monthlyLimitPaisa?.let(::Money),
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun Budget.toEntity(): BudgetEntity = BudgetEntity(
    id = id,
    scopeKey = categoryId ?: BudgetEntity.OVERALL,
    month = month?.toString(),
    monthlyLimitPaisa = monthlyLimit?.paisa,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecurringSeriesEntity.toDomain(): RecurringSeries = RecurringSeries(
    id = id,
    amount = Money(amountPaisa),
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    dayOfMonth = dayOfMonth,
    startMonth = YearMonth.parse(startMonth),
    lastGeneratedMonth = lastGeneratedMonth?.let { YearMonth.parse(it) },
    isPaused = isPaused,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

internal fun RecurringSeries.toEntity(): RecurringSeriesEntity = RecurringSeriesEntity(
    id = id,
    amountPaisa = amount.paisa,
    categoryId = categoryId,
    note = note,
    paymentMethodId = paymentMethodId,
    dayOfMonth = dayOfMonth,
    startMonth = startMonth.toString(),
    lastGeneratedMonth = lastGeneratedMonth?.toString(),
    isPaused = isPaused,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
