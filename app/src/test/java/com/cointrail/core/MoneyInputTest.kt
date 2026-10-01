package com.cointrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyInputTest {

    @Test
    fun `empty input is worth nothing and cannot be saved`() {
        val input = MoneyInput.empty()

        assertEquals("৳0", input.display())
        assertNull(input.toMoney())
        assertFalse(input.canSave)
    }

    @Test
    fun `digits build a whole taka amount`() {
        val input = MoneyInput.empty().appendDigit('1').appendDigit('2')
            .appendDigit('5').appendDigit('0')

        assertEquals("1250", input.text)
        assertEquals(Money(125_000), input.toMoney())
        assertTrue(input.canSave)
    }

    @Test
    fun `taka entry drops a meaningless leading zero`() {
        val input = MoneyInput.empty().appendDigit('0').appendDigit('5')

        assertEquals("5", input.text)
    }

    @Test
    fun `zero on its own is not saveable`() {
        val input = MoneyInput.empty().appendDigit('0')

        assertEquals(Money.ZERO, input.toMoney())
        assertFalse(input.canSave)
    }

    @Test
    fun `decimal starts a paisa part`() {
        val input = MoneyInput.empty().appendDecimal()

        assertEquals("0.", input.text)
        assertNull(input.toMoney())
    }

    @Test
    fun `decimal accepts at most two paisa digits`() {
        val input = MoneyInput.empty()
            .appendDigit('1').appendDecimal()
            .appendDigit('2').appendDigit('3').appendDigit('4')

        assertEquals("1.23", input.text)
        assertEquals(Money(123), input.toMoney())
    }

    @Test
    fun `second decimal point is ignored`() {
        val input = MoneyInput.empty().appendDigit('1').appendDecimal()
            .appendDigit('2').appendDecimal()

        assertEquals("1.2", input.text)
    }

    @Test
    fun `a single paisa digit means tens of paisa`() {
        val input = MoneyInput.empty().appendDecimal().appendDigit('5')

        assertEquals("0.5", input.text)
        assertEquals(Money(50), input.toMoney())
    }

    @Test
    fun `backspace removes the last keystroke`() {
        val input = MoneyInput.empty().appendDigit('1').appendDigit('2').backspace()

        assertEquals("1", input.text)

        val neverNegative = input.backspace().backspace().backspace()
        assertEquals("", neverNegative.text)
    }

    @Test
    fun `display groups thousands the way money is shown`() {
        assertEquals("৳1,250", MoneyInput.empty().appendDigit('1').appendDigit('2').appendDigit('5').appendDigit('0').display())
        assertEquals("৳100,000", MoneyInput.empty().appendDigit('1').appendDigit('0').appendDigit('0').appendDigit('0').appendDigit('0').appendDigit('0').display())
    }

    @Test
    fun `display keeps the partially typed paisa part`() {
        assertEquals("৳1,250.5", MoneyInput.empty().appendDigit('1').appendDigit('2').appendDigit('5').appendDigit('0').appendDecimal().appendDigit('5').display())
        assertEquals("৳1,250.", MoneyInput.empty().appendDigit('1').appendDigit('2').appendDigit('5').appendDigit('0').appendDecimal().display())
    }

    @Test
    fun `display can omit the symbol`() {
        assertEquals("1,250", MoneyInput.empty().appendDigit('1').appendDigit('2').appendDigit('5').appendDigit('0').display(withSymbol = false))
    }

    @Test
    fun `after a decimal a lone zero integer part stays intact`() {
        val input = MoneyInput.empty().appendDigit('0').appendDecimal().appendDigit('5')

        assertEquals("0.5", input.text)
    }
}
