package com.cointrail.core

import com.cointrail.domain.model.Expense
import java.time.format.DateTimeFormatter

/**
 * Renders expenses as a small, dependency-free CSV for the export feature (SPEC §6.9).
 *
 * The output is deliberately stable so it stays readable by Excel, a future version of the app or
 * a human a decade from now: ISO `YYYY-MM-DD` dates, ISO `HH:MM:SS` times, and amounts as plain
 * dot-decimal taka with exactly two decimals (no locale separators or currency symbols).
 */
object ExpenseCsv {

    const val HEADER: String = "date,time,amount_taka,category,payment_method,note"

    private val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun render(
        expenses: List<Expense>,
        categoryNames: Map<String, String>,
        paymentMethodNames: Map<String, String>,
    ): String {
        val lines = ArrayList<String>(expenses.size + 1)
        lines += HEADER
        for (expense in expenses) {
            lines += listOf(
                expense.occurredAt.format(DATE),
                expense.occurredAt.format(TIME),
                formatTaka(expense.amount.paisa),
                categoryNames[expense.categoryId] ?: expense.categoryId,
                expense.paymentMethodId?.let { paymentMethodNames[it] } ?: "",
                expense.note ?: "",
            ).joinToString(",") { escape(it) }
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    private fun formatTaka(paisa: Long): String {
        val sign = if (paisa < 0) "-" else ""
        val abs = if (paisa < 0) -paisa else paisa
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }

    private fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
