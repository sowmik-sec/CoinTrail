package com.cointrail.data.alerts

import androidx.test.core.app.ApplicationProvider
import com.cointrail.domain.budget.BudgetAlertKey
import com.cointrail.domain.budget.BudgetAlertLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesBudgetAlertStoreTest {

    private val october: YearMonth = YearMonth.of(2026, 10)

    private fun store() =
        SharedPreferencesBudgetAlertStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `records and reads back fired thresholds`() = runTest {
        val store = store()
        val keys = setOf(
            BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING),
            BudgetAlertKey("__overall__", BudgetAlertLevel.EXCEEDED),
        )

        store.markFired(october, keys)

        assertEquals(keys, store.firedFor(october))
    }

    @Test
    fun `a month with nothing recorded is empty`() = runTest {
        assertTrue(store().firedFor(october).isEmpty())
    }

    @Test
    fun `thresholds are scoped per month`() = runTest {
        val store = store()
        store.markFired(october, setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING)))

        assertTrue(store.firedFor(YearMonth.of(2026, 11)).isEmpty())
    }

    @Test
    fun `marking is cumulative within a month`() = runTest {
        val store = store()
        store.markFired(october, setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING)))
        store.markFired(october, setOf(BudgetAlertKey("b1", BudgetAlertLevel.EXCEEDED)))

        assertEquals(
            setOf(
                BudgetAlertKey("b1", BudgetAlertLevel.WARNING),
                BudgetAlertKey("b1", BudgetAlertLevel.EXCEEDED),
            ),
            store.firedFor(october),
        )
    }

    @Test
    fun `recording a newer month prunes the older ones`() = runTest {
        val store = store()
        store.markFired(YearMonth.of(2026, 8), setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING)))
        store.markFired(YearMonth.of(2026, 9), setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING)))

        store.markFired(october, setOf(BudgetAlertKey("b1", BudgetAlertLevel.WARNING)))

        assertTrue(store.firedFor(YearMonth.of(2026, 8)).isEmpty())
        assertTrue(store.firedFor(YearMonth.of(2026, 9)).isEmpty())
        assertEquals(1, store.firedFor(october).size)
    }

    @Test
    fun `a budget id containing the separator round-trips`() = runTest {
        val store = store()
        val key = BudgetAlertKey("budget|weird", BudgetAlertLevel.EXCEEDED)

        store.markFired(october, setOf(key))

        assertEquals(setOf(key), store.firedFor(october))
    }
}
