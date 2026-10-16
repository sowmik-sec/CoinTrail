package com.cointrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry rules behind both keypads (ticket 26), asserted once: the amount machine, the can-save
 * rule, the payment-method toggle and the collapsed note. The screen tests keep only what differs
 * between quick-add and edit.
 */
class ExpenseDraftTest {

    @Test
    fun `a fresh draft starts empty with the note collapsed and cannot save`() {
        val draft = ExpenseDraft()

        assertEquals("", draft.amount.text)
        assertEquals("৳0", draft.amount.display())
        assertNull(draft.categoryId)
        assertNull(draft.paymentMethodId)
        assertEquals("", draft.note)
        assertFalse(draft.noteExpanded)
        assertFalse(draft.canSave)
    }

    @Test
    fun `digits assemble the amount with at most two paisa digits`() {
        var draft = ExpenseDraft()
        "12.50".forEach { char ->
            draft = if (char == '.') draft.onDecimal() else draft.onDigit(char)
        }

        assertEquals("12.50", draft.amount.text)
        assertEquals(Money(1250), draft.money())

        // A third paisa digit is ignored.
        assertEquals("12.50", draft.onDigit('5').amount.text)
    }

    @Test
    fun `a single decimal point is accepted and backspace retracts it`() {
        var draft = ExpenseDraft().onDigit('5').onDecimal()

        assertEquals("5.", draft.amount.text)

        draft = draft.onBackspace()

        assertEquals("5", draft.amount.text)
    }

    @Test
    fun `a zero amount is never saveable`() {
        val draft = ExpenseDraft().onDigit('0')

        assertFalse(draft.canSave)
    }

    @Test
    fun `a positive amount without a category cannot save`() {
        val draft = ExpenseDraft().onDigit('5')

        assertTrue(draft.money()!! > Money.ZERO)
        assertFalse(draft.canSave)

        assertTrue(draft.selectCategory("preset-food").canSave)
    }

    @Test
    fun `selecting a category sets it and keeps it selected`() {
        val draft = ExpenseDraft().selectCategory("preset-food")

        assertEquals("preset-food", draft.categoryId)
        assertEquals("preset-food", draft.selectCategory("preset-food").categoryId)
    }

    @Test
    fun `tapping the selected payment method again clears it`() {
        var draft = ExpenseDraft().selectPaymentMethod("pm-cash")

        assertEquals("pm-cash", draft.paymentMethodId)

        draft = draft.selectPaymentMethod("pm-cash")

        assertNull(draft.paymentMethodId)
    }

    @Test
    fun `the note holds whatever is typed and toggles between collapsed and expanded`() {
        var draft = ExpenseDraft().setNote("lunch").toggleNoteExpanded()

        assertTrue(draft.noteExpanded)
        assertEquals("lunch", draft.note)

        draft = draft.toggleNoteExpanded()

        assertFalse(draft.noteExpanded)
        assertEquals("lunch", draft.note)
    }
}
