package com.cointrail.core

import com.cointrail.domain.model.Expense
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ExpenseCsvTest {

    private val categoryNames = mapOf("preset-food" to "Food", "preset-transport" to "Transport")
    private val paymentMethodNames = mapOf("pm-cash" to "Cash", "pm-bkash" to "bKash")

    private fun expense(
        id: String = "e1",
        paisa: Long,
        at: LocalDateTime,
        categoryId: String = "preset-food",
        note: String? = null,
        paymentMethodId: String? = null,
    ) = Expense(
        id = id,
        amount = Money(paisa),
        categoryId = categoryId,
        note = note,
        paymentMethodId = paymentMethodId,
        occurredAt = at,
        createdAt = at,
        updatedAt = at,
    )

    @Test
    fun `renders the header when there are no expenses`() {
        val csv = ExpenseCsv.render(emptyList(), categoryNames, paymentMethodNames)

        assertEquals("date,time,amount_taka,category,payment_method,note\n", csv)
    }

    @Test
    fun `renders a row with ISO date time and a two decimal amount`() {
        val csv = ExpenseCsv.render(
            listOf(expense(paisa = 125_050, at = LocalDateTime.of(2026, 10, 5, 13, 5, 9))),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,13:05:09,1250.50,Food,,\n",
            csv,
        )
    }

    @Test
    fun `whole taka amounts still carry two decimals`() {
        val csv = ExpenseCsv.render(
            listOf(expense(paisa = 250_000, at = LocalDateTime.of(2026, 10, 5, 9, 0, 0))),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,09:00:00,2500.00,Food,,\n",
            csv,
        )
    }

    @Test
    fun `renders category and payment method names`() {
        val csv = ExpenseCsv.render(
            listOf(
                expense(
                    paisa = 5_000,
                    at = LocalDateTime.of(2026, 10, 5, 8, 30, 0),
                    categoryId = "preset-transport",
                    paymentMethodId = "pm-bkash",
                    note = "rickshaw",
                ),
            ),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,08:30:00,50.00,Transport,bKash,rickshaw\n",
            csv,
        )
    }

    @Test
    fun `an unknown category falls back to its id and a missing payment method is blank`() {
        val csv = ExpenseCsv.render(
            listOf(expense(paisa = 100, at = LocalDateTime.of(2026, 10, 5, 8, 0, 0), categoryId = "custom-1")),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,08:00:00,1.00,custom-1,,\n",
            csv,
        )
    }

    @Test
    fun `fields containing separators or quotes are escaped`() {
        val csv = ExpenseCsv.render(
            listOf(
                expense(
                    paisa = 100,
                    at = LocalDateTime.of(2026, 10, 5, 8, 0, 0),
                    note = "lunch, \"big\" one",
                ),
            ),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,08:00:00,1.00,Food,,\"lunch, \"\"big\"\" one\"\n",
            csv,
        )
    }

    @Test
    fun `notes with newlines are quoted`() {
        val csv = ExpenseCsv.render(
            listOf(expense(paisa = 100, at = LocalDateTime.of(2026, 10, 5, 8, 0, 0), note = "line one\nline two")),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-05,08:00:00,1.00,Food,,\"line one\nline two\"\n",
            csv,
        )
    }

    @Test
    fun `renders one row per expense in the order given`() {
        val csv = ExpenseCsv.render(
            listOf(
                expense(id = "a", paisa = 100, at = LocalDateTime.of(2026, 10, 1, 8, 0, 0)),
                expense(id = "b", paisa = 200, at = LocalDateTime.of(2026, 10, 2, 8, 0, 0)),
            ),
            categoryNames,
            paymentMethodNames,
        )

        assertEquals(
            "date,time,amount_taka,category,payment_method,note\n" +
                "2026-10-01,08:00:00,1.00,Food,,\n" +
                "2026-10-02,08:00:00,2.00,Food,,\n",
            csv,
        )
    }
}
