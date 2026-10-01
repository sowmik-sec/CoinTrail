package com.cointrail.domain.reports

import com.cointrail.core.Money
import com.cointrail.domain.model.CategoryTotal
import kotlin.math.abs

/** One category's spending this month compared with the previous month (SPEC §6.4). */
data class CategoryComparison(
    val categoryId: String,
    val current: Money,
    val previous: Money,
) {
    /** Positive when spending rose, negative when it fell. */
    val delta: Money get() = current - previous
}

/** A whole month measured against the month before it: the total delta plus a per-category breakdown. */
data class MonthComparison(
    val currentTotal: Money,
    val previousTotal: Money,
    val categories: List<CategoryComparison>,
) {
    val delta: Money get() = currentTotal - previousTotal
}

object MonthComparisonCalculator {

    /**
     * Compares two months' per-category totals. Every category that spent in either month appears,
     * with the missing side read as zero, ordered by the size of the change (biggest first) so the
     * categories that moved most are seen first. Totals are the sum of each month's categories.
     */
    fun compare(current: List<CategoryTotal>, previous: List<CategoryTotal>): MonthComparison {
        val currentByCategory = current.associate { it.categoryId to it.total }
        val previousByCategory = previous.associate { it.categoryId to it.total }
        val categories = (currentByCategory.keys + previousByCategory.keys)
            .map { id ->
                CategoryComparison(
                    categoryId = id,
                    current = currentByCategory[id] ?: Money.ZERO,
                    previous = previousByCategory[id] ?: Money.ZERO,
                )
            }
            .sortedWith(BIGGEST_MOVE_FIRST)
        return MonthComparison(
            currentTotal = current.fold(Money.ZERO) { acc, item -> acc + item.total },
            previousTotal = previous.fold(Money.ZERO) { acc, item -> acc + item.total },
            categories = categories,
        )
    }

    private val BIGGEST_MOVE_FIRST: Comparator<CategoryComparison> =
        compareByDescending<CategoryComparison> { abs(it.delta.paisa) }
            .thenByDescending { it.current.paisa }
            .thenBy { it.categoryId }
}
