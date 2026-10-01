package com.cointrail.core

/**
 * The amount-entry state machine behind the in-app keypad. It only ever holds what the user
 * typed (taka, optionally a decimal point and up to two paisa digits) and converts that to
 * [Money] once it forms a complete number, or to grouped display text for the entry field.
 * No system keyboard is involved.
 */
class MoneyInput private constructor(val text: String) {

    fun toMoney(): Money? {
        if (text.isEmpty() || text.endsWith(".")) return null
        return Money.fromTaka(text)
    }

    val canSave: Boolean
        get() {
            val money = toMoney() ?: return false
            return money > Money.ZERO
        }

    /** The amount as shown while it is being typed, grouped per SPEC §4 (e.g. `৳1,250.50`). */
    fun display(withSymbol: Boolean = true): String {
        val symbol = if (withSymbol) "৳" else ""
        if (text.isEmpty()) return symbol + "0"
        val dot = text.indexOf('.')
        if (dot < 0) return "$symbol${groupThousands(text)}"
        return "$symbol${groupThousands(text.substring(0, dot))}${text.substring(dot)}"
    }

    fun appendDigit(digit: Char): MoneyInput {
        require(digit.isDigit()) { "Only digits can be appended" }
        if (text == "0") return MoneyInput(digit.toString())
        if (text.contains('.')) {
            val paisaDigits = text.length - text.indexOf('.') - 1
            if (paisaDigits >= 2) return this
        }
        return MoneyInput(text + digit)
    }

    fun appendDecimal(): MoneyInput = when {
        text.isEmpty() -> MoneyInput("0.")
        text.contains('.') -> this
        else -> MoneyInput("$text.")
    }

    fun backspace(): MoneyInput = if (text.isEmpty()) this else MoneyInput(text.dropLast(1))

    companion object {
        fun empty(): MoneyInput = MoneyInput("")

        /** Seeds the entry field from an existing amount, e.g. when editing a logged expense. */
        fun fromMoney(money: Money): MoneyInput {
            val taka = money.paisa / 100
            val paisa = money.paisa % 100
            return if (paisa == 0L) MoneyInput(taka.toString()) else MoneyInput("$taka.${paisa.toString().padStart(2, '0')}")
        }
    }

    private fun groupThousands(digits: String): String {
        val grouped = StringBuilder()
        for ((index, digit) in digits.withIndex()) {
            val remaining = digits.length - index
            grouped.append(digit)
            if (remaining > 1 && remaining % 3 == 1) grouped.append(',')
        }
        return grouped.toString()
    }
}
