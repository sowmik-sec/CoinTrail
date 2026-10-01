package com.cointrail.data

import com.cointrail.domain.model.Category
import com.cointrail.domain.model.PaymentMethod
import java.time.LocalDateTime

object SeedData {

    private val SEED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    val categories: List<Category> = listOf(
        Category(id = "preset-food", name = "Food", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        Category(id = "preset-groceries", name = "Groceries", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        Category(id = "preset-transport", name = "Transport", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        Category(id = "preset-utilities", name = "Utilities", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
        Category(id = "preset-rent", name = "Rent", isPreset = true, sortOrder = 4, updatedAt = SEED_TIME),
        Category(id = "preset-health", name = "Health", isPreset = true, sortOrder = 5, updatedAt = SEED_TIME),
        Category(id = "preset-shopping", name = "Shopping", isPreset = true, sortOrder = 6, updatedAt = SEED_TIME),
        Category(id = "preset-entertainment", name = "Entertainment", isPreset = true, sortOrder = 7, updatedAt = SEED_TIME),
        Category(id = "preset-other", name = "Other", isPreset = true, sortOrder = 8, updatedAt = SEED_TIME),
    )

    val paymentMethods: List<PaymentMethod> = listOf(
        PaymentMethod(id = "pm-cash", name = "Cash", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-bkash", name = "bKash", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-nagad", name = "Nagad", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        PaymentMethod(id = "pm-card", name = "Card", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
    )
}
