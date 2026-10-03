package com.cointrail.domain.model

import com.cointrail.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth

class ModelsTest {

    private val ts: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0)

    private fun expense(amount: Money = Money(100), categoryId: String = "preset-food") = Expense(
        amount = amount,
        categoryId = categoryId,
        note = null,
        paymentMethodId = null,
        occurredAt = ts,
        createdAt = ts,
        updatedAt = ts,
    )

    @Test
    fun `expense rejects non-positive amount`() {
        assertThrows(IllegalArgumentException::class.java) { expense(amount = Money.ZERO) }
    }

    @Test
    fun `expense rejects blank category`() {
        assertThrows(IllegalArgumentException::class.java) { expense(categoryId = " ") }
    }

    @Test
    fun `expense isDeleted reflects tombstone`() {
        assertFalse(expense().isDeleted)
        assertTrue(expense().copy(deletedAt = ts).isDeleted)
    }

    @Test
    fun `category and payment method reject blank names`() {
        assertThrows(IllegalArgumentException::class.java) {
            Category(id = "c", name = "", isPreset = false, isHidden = false, sortOrder = 0, updatedAt = ts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaymentMethod(id = "p", name = " ", isPreset = false, isHidden = false, sortOrder = 0, updatedAt = ts)
        }
    }

    @Test
    fun `a default budget requires a positive limit`() {
        assertThrows(IllegalArgumentException::class.java) {
            Budget(id = "b", categoryId = null, monthlyLimit = Money.ZERO, updatedAt = ts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Budget(id = "b", categoryId = null, month = null, monthlyLimit = null, updatedAt = ts)
        }
    }

    @Test
    fun `an override limit is optional but positive when present`() {
        val eid = YearMonth.of(2027, 3)
        assertThrows(IllegalArgumentException::class.java) {
            Budget(id = "b", categoryId = null, month = eid, monthlyLimit = Money.ZERO, updatedAt = ts)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Budget(id = "b", categoryId = null, month = eid, monthlyLimit = Money(-1), updatedAt = ts)
        }
        // Null limit with a month is the explicit "no budget" state and is valid.
        val noBudget = Budget(id = "b", categoryId = "preset-food", month = eid, monthlyLimit = null, updatedAt = ts)
        assertTrue(noBudget.isNoBudget)
        assertFalse(noBudget.isDefault)
    }

    @Test
    fun `budget scope key falls back to the overall scope`() {
        assertEquals(Budget.OVERALL_SCOPE, Budget(id = "b", categoryId = null, monthlyLimit = Money(1), updatedAt = ts).scopeKey)
        assertEquals(
            "preset-food",
            Budget(id = "b", categoryId = "preset-food", monthlyLimit = Money(1), updatedAt = ts).scopeKey,
        )
    }

    @Test
    fun `recurring series rejects invalid day and amount`() {
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSeries(
                id = "r", amount = Money(100), categoryId = "preset-rent", note = null,
                paymentMethodId = null, dayOfMonth = 32, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = null, isPaused = false, updatedAt = ts,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            RecurringSeries(
                id = "r", amount = Money.ZERO, categoryId = "preset-rent", note = null,
                paymentMethodId = null, dayOfMonth = 5, startMonth = YearMonth.of(2026, 10),
                lastGeneratedMonth = null, isPaused = false, updatedAt = ts,
            )
        }
    }
}
