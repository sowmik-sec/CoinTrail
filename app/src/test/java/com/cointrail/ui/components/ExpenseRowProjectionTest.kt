package com.cointrail.ui.components

import com.cointrail.core.Money
import com.cointrail.domain.model.CatalogItem
import com.cointrail.domain.model.Expense
import com.cointrail.testing.TEST_TIME
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The one projection, asserted at its seam (ticket 27): the same expense plus the same catalogue
 * builds the identical row everywhere, unknown ids fall back to the id, and a missing payment
 * method stays missing.
 */
class ExpenseRowProjectionTest {

    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
    private val categories = listOf(CatalogItem(id = "preset-food", name = "Food", updatedAt = TEST_TIME))
    private val paymentMethods = listOf(CatalogItem(id = "pm-cash", name = "Cash", updatedAt = TEST_TIME))

    private val expense = Expense(
        id = "e1",
        amount = Money(12_550),
        categoryId = "preset-food",
        note = "with the team",
        paymentMethodId = "pm-cash",
        occurredAt = LocalDateTime.of(2026, 10, 5, 13, 5),
        createdAt = TEST_TIME,
        updatedAt = TEST_TIME,
    )

    private val categoryNames = categories.namesById()
    private val paymentMethodNames = paymentMethods.namesById()

    @Test
    fun `a row carries the category name payment method name note time and amount`() {
        val row = expense.toRow(categoryNames, paymentMethodNames, timeFormat)

        assertEquals("e1", row.id)
        assertEquals(Money(12_550), row.amount)
        assertEquals("Food", row.categoryName)
        assertEquals("Cash", row.paymentMethodName)
        assertEquals("with the team", row.note)
        assertEquals("13:05", row.time)
    }

    @Test
    fun `the same expense builds identical rows wherever it is listed`() {
        // Home and the Day screen call this projection with their own formatter; with equal inputs
        // the rows are equal, which is what makes the two lists agree about the same expense.
        val home = expense.toRow(categoryNames, paymentMethodNames, timeFormat)
        val day = expense.toRow(categoryNames, paymentMethodNames, timeFormat)

        assertEquals(home, day)
    }

    @Test
    fun `an unknown category falls back to its id and an unknown payment method stays missing`() {
        val row = expense.copy(categoryId = "gone").toRow(categoryNames, paymentMethodNames, timeFormat)
        val noPayment = expense.copy(paymentMethodId = null).toRow(categoryNames, paymentMethodNames, timeFormat)
        val goneMethod = expense.copy(paymentMethodId = "pm-gone").toRow(categoryNames, paymentMethodNames, timeFormat)

        assertEquals("gone", row.categoryName)
        assertNull(noPayment.paymentMethodName)
        assertNull(goneMethod.paymentMethodName)
    }
}
