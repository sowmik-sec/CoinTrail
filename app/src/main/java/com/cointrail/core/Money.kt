package com.cointrail.core

import java.text.NumberFormat
import java.util.Locale

data class Money(val paisa: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(paisa + other.paisa)

    operator fun minus(other: Money): Money = Money(paisa - other.paisa)

    override fun compareTo(other: Money): Int = paisa.compareTo(other.paisa)

    fun format(withSymbol: Boolean = true): String {
        val sign = if (paisa < 0) "-" else ""
        val abs = if (paisa < 0) -paisa else paisa
        val taka = if (withSymbol) integerFormat.format(abs / 100) else (abs / 100).toString()
        val remainder = (abs % 100).toInt()
        val number = if (remainder == 0) taka else "$taka.${remainder.toString().padStart(2, '0')}"
        val symbol = if (withSymbol) "৳" else ""
        return "$sign$symbol$number"
    }

    companion object {
        val ZERO: Money = Money(0)

        private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)

        fun fromTaka(input: String): Money? {
            val cleaned = input.trim().replace("৳", "").replace(",", "").replace(" ", "")
            if (cleaned.isEmpty()) return null
            val negative = cleaned.startsWith("-")
            val unsigned = if (negative) cleaned.substring(1) else cleaned
            val parts = unsigned.split(".")
            if (parts.size > 2) return null
            val takaPart = parts[0]
            val paisaPart = if (parts.size == 2) parts[1] else ""
            val hasValidTaka = takaPart.isNotEmpty() && takaPart.all { it.isDigit() }
            if (!hasValidTaka) return null
            val hasValidPaisa = paisaPart.length <= 2 && paisaPart.all { it.isDigit() }
            if (!hasValidPaisa) return null
            val taka = takaPart.toLongOrNull() ?: return null
            val paisa = paisaPart.padEnd(2, '0').toLong()
            val total = taka * 100 + paisa
            return Money(if (negative) -total else total)
        }
    }
}
