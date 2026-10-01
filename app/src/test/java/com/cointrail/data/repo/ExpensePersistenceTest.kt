package com.cointrail.data.repo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpensePersistenceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val accountKey = "persistence-test"
    private val at: LocalDateTime = LocalDateTime.of(2026, 10, 5, 13, 0)
    private val dayStart: LocalDateTime = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd: LocalDateTime = LocalDateTime.of(2026, 10, 6, 0, 0)

    @After
    fun tearDown() {
        context.deleteDatabase(CoinTrailDatabase.databaseName(accountKey))
    }

    @Test
    fun `a logged expense survives closing and reopening the database`() = runBlocking {
        val first = CoinTrailDatabase.create(context, accountKey)
        val id = ExpenseRepository(first.expenseDao()) { at }
            .add(Money(12_550), "preset-food", "lunch", "pm-cash", at)
        first.close()

        val reopened = CoinTrailDatabase.create(context, accountKey)
        try {
            val rows = reopened.expenseDao().observeBetween(dayStart, dayEnd).first()
            assertEquals(listOf(id), rows.map { it.id })
            assertEquals(12_550L, rows.single().amountPaisa)
            assertEquals("lunch", rows.single().note)
            assertEquals("pm-cash", rows.single().paymentMethodId)
        } finally {
            reopened.close()
        }
    }
}
