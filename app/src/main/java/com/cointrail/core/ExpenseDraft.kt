package com.cointrail.core

/**
 * An expense being entered or edited (CONTEXT: expense draft) — the amount being typed, the
 * selected category and payment method, and the note. The entry rules live here, stated once for
 * both keypads: at most two paisa digits and a single decimal point (by [MoneyInput]), a zero
 * amount never saveable, a category required, any amount keystroke delivered atomically, the note
 * starting collapsed, and tapping the already-selected payment method clearing it. The draft knows
 * nothing about the catalogue — the screens supply the picker lists — so quick-add and edit can no
 * longer drift apart. Saving, resetting, loading and the timestamp remain the screens' business.
 */
data class ExpenseDraft(
    val amount: MoneyInput = MoneyInput.empty(),
    val categoryId: String? = null,
    val paymentMethodId: String? = null,
    val note: String = "",
    val noteExpanded: Boolean = false,
) {

    /** A positive amount and a selected category; note and payment method are optional. */
    val canSave: Boolean get() = amount.canSave && categoryId != null

    /** The typed amount as [Money], or null while it is not a complete positive number. */
    fun money(): Money? = amount.toMoney()

    fun onDigit(digit: Char): ExpenseDraft = copy(amount = amount.appendDigit(digit))

    fun onDecimal(): ExpenseDraft = copy(amount = amount.appendDecimal())

    fun onBackspace(): ExpenseDraft = copy(amount = amount.backspace())

    fun selectCategory(categoryId: String): ExpenseDraft = copy(categoryId = categoryId)

    /** Tapping the selected payment method again clears it: payment stays optional. */
    fun selectPaymentMethod(paymentMethodId: String): ExpenseDraft =
        copy(paymentMethodId = if (this.paymentMethodId == paymentMethodId) null else paymentMethodId)

    fun setNote(note: String): ExpenseDraft = copy(note = note)

    fun toggleNoteExpanded(): ExpenseDraft = copy(noteExpanded = !noteExpanded)
}
