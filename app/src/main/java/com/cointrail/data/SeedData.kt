package com.cointrail.data

import com.cointrail.domain.model.CatalogItem
import java.time.LocalDateTime

object SeedData {

    private val SEED_TIME: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    val categories: List<CatalogItem> = listOf(
        CatalogItem(id = "preset-food", name = "Food", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-groceries", name = "Groceries", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-transport", name = "Transport", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-utilities", name = "Utilities", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-rent", name = "Rent", isPreset = true, sortOrder = 4, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-health", name = "Health", isPreset = true, sortOrder = 5, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-shopping", name = "Shopping", isPreset = true, sortOrder = 6, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-entertainment", name = "Entertainment", isPreset = true, sortOrder = 7, updatedAt = SEED_TIME),
        CatalogItem(id = "preset-other", name = "Other", isPreset = true, sortOrder = 8, updatedAt = SEED_TIME),
    )

    val paymentMethods: List<CatalogItem> = listOf(
        CatalogItem(id = "pm-cash", name = "Cash", isPreset = true, sortOrder = 0, updatedAt = SEED_TIME),
        CatalogItem(id = "pm-bkash", name = "bKash", isPreset = true, sortOrder = 1, updatedAt = SEED_TIME),
        CatalogItem(id = "pm-nagad", name = "Nagad", isPreset = true, sortOrder = 2, updatedAt = SEED_TIME),
        CatalogItem(id = "pm-card", name = "Card", isPreset = true, sortOrder = 3, updatedAt = SEED_TIME),
    )
}
