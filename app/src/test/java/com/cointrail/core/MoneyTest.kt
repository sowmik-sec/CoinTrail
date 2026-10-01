package com.cointrail.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats whole taka without decimals`() {
        assertEquals("৳1,250", Money(125_000).format())
    }

    @Test
    fun `formats paisa when non-zero`() {
        assertEquals("৳1,250.50", Money(125_050).format())
    }

    @Test
    fun `formats zero and can omit symbol`() {
        assertEquals("৳0", Money.ZERO.format())
        assertEquals("1250", Money(125_000).format(withSymbol = false))
    }

    @Test
    fun `adds and subtracts`() {
        assertEquals(Money(300), Money(100) + Money(200))
        assertEquals(Money(100), Money(300) - Money(200))
    }

    @Test
    fun `compares by paisa`() {
        assertTrue(Money(200) > Money(100))
    }

    @Test
    fun `parses plain taka`() {
        assertEquals(Money(125_000), Money.fromTaka("1250"))
    }

    @Test
    fun `parses symbol separators and decimal paisa`() {
        assertEquals(Money(125_050), Money.fromTaka("৳1,250.5"))
        assertEquals(Money(125_055), Money.fromTaka("1250.55"))
        assertEquals(Money(50), Money.fromTaka("0.5"))
    }

    @Test
    fun `rejects garbage`() {
        assertNull(Money.fromTaka(""))
        assertNull(Money.fromTaka("৳"))
        assertNull(Money.fromTaka("12.a"))
        assertNull(Money.fromTaka("1.234"))
        assertNull(Money.fromTaka("12.5.5"))
    }
}
