package com.cointrail.data.alerts

import com.cointrail.core.Money
import com.cointrail.domain.budget.BudgetAlert
import com.cointrail.domain.budget.BudgetAlertKey
import com.cointrail.domain.budget.BudgetAlertLevel
import com.cointrail.domain.budget.BudgetProgress
import com.cointrail.testing.FakeBudgetAlertStore
import com.cointrail.testing.FakeBudgetNotifier
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class BudgetAlertTrackerTest {

    private val october: YearMonth = YearMonth.of(2026, 10)

    private fun progress(
        id: String,
        spentPaisa: Long,
        limitPaisa: Long = 10_000,
        categoryId: String = "preset-food",
    ) = BudgetProgress(
        budgetId = id,
        categoryId = categoryId,
        label = "Food",
        limit = Money(limitPaisa),
        spent = Money(spentPaisa),
    )

    private fun tracker(
        store: FakeBudgetAlertStore = FakeBudgetAlertStore(),
        notifier: FakeBudgetNotifier = FakeBudgetNotifier(),
        month: YearMonth = october,
    ) = BudgetAlertTracker(store, notifier) { month }

    @Test
    fun `crossing a threshold posts one notification and records it`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()

        tracker(store, notifier).evaluate(listOf(progress("b1", 8_500)))

        assertEquals(listOf(BudgetAlertLevel.WARNING), notifier.alerts.map { it.level })
        assertEquals(
            setOf(BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING)),
            store.firedFor(october),
        )
    }

    @Test
    fun `re-evaluating the same progress does not notify again`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()
        val tracker = tracker(store, notifier)

        tracker.evaluate(listOf(progress("b1", 8_500)))
        tracker.evaluate(listOf(progress("b1", 9_000)))

        assertEquals(1, notifier.alerts.size)
    }

    @Test
    fun `reaching the limit later fires the exceeded alert on top of the warning`() = runTest {
        val notifier = FakeBudgetNotifier()
        val tracker = tracker(notifier = notifier)

        tracker.evaluate(listOf(progress("b1", 8_500)))
        tracker.evaluate(listOf(progress("b1", 10_200)))

        assertEquals(
            listOf(BudgetAlertLevel.WARNING, BudgetAlertLevel.EXCEEDED),
            notifier.alerts.map { it.level },
        )
    }

    @Test
    fun `the same threshold re-arms in a new month`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()

        BudgetAlertTracker(store, notifier) { YearMonth.of(2026, 10) }.evaluate(listOf(progress("b1", 8_500)))
        BudgetAlertTracker(store, notifier) { YearMonth.of(2026, 11) }.evaluate(listOf(progress("b1", 8_500)))

        assertEquals(2, notifier.alerts.size)
    }

    @Test
    fun `no budgets means nothing is evaluated`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()

        tracker(store, notifier).evaluate(emptyList())

        assertTrue(notifier.alerts.isEmpty())
        assertTrue(store.firedFor(october).isEmpty())
    }

    @Test
    fun `jumping past the limit records the subsumed warning alongside the exceeded alert`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()

        tracker(store, notifier).evaluate(listOf(progress("b1", 15_000)))

        assertEquals(listOf(BudgetAlertLevel.EXCEEDED), notifier.alerts.map { it.level })
        assertEquals(
            setOf(
                BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING),
                BudgetAlertKey("preset-food", BudgetAlertLevel.EXCEEDED),
            ),
            store.firedFor(october),
        )
    }

    @Test
    fun `a mid-month budget change never re-arms a threshold already crossed`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()
        val tracker = tracker(store, notifier)

        // The warning fires against the month's original budget row…
        tracker.evaluate(listOf(progress("original-row", 8_500)))
        assertEquals(1, notifier.alerts.size)

        // …then the user restates the budget into a new row (new id, same scope) with a lower
        // limit. The warning must not fire again even though spending is still past 80%; spending
        // now crosses 100% of the *new* limit for the first time, so exceeded fires exactly once.
        tracker.evaluate(listOf(progress("restated-row", 9_500, limitPaisa = 9_600)))
        tracker.evaluate(listOf(progress("restated-row", 10_500, limitPaisa = 9_600)))
        tracker.evaluate(listOf(progress("restated-row", 11_000, limitPaisa = 9_600)))

        assertEquals(
            listOf(BudgetAlertLevel.WARNING, BudgetAlertLevel.EXCEEDED),
            notifier.alerts.map { it.level },
        )
        assertEquals(
            setOf(
                BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING),
                BudgetAlertKey("preset-food", BudgetAlertLevel.EXCEEDED),
            ),
            store.firedFor(october),
        )
    }

    @Test
    fun `a budget removed mid-month does not un-bookkeep or re-arm the fired thresholds`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()
        val tracker = tracker(store, notifier)

        tracker.evaluate(listOf(progress("original-row", 8_500)))
        // "No budget" scopes drop out of progress entirely; evaluating without them changes nothing.
        tracker.evaluate(emptyList())

        assertEquals(1, notifier.alerts.size)
        assertEquals(1, store.firedFor(october).size)
    }

    @Test
    fun `each scope fires once and independently in the same month`() = runTest {
        val store = FakeBudgetAlertStore()
        val notifier = FakeBudgetNotifier()

        tracker(store, notifier).evaluate(
            listOf(
                progress("food-row", 8_500, categoryId = "preset-food"),
                progress("rent-row", 12_000, categoryId = "preset-rent"),
            ),
        )

        assertEquals(
            listOf(BudgetAlertLevel.WARNING, BudgetAlertLevel.EXCEEDED),
            notifier.alerts.map { it.level },
        )
        assertEquals(
            setOf(
                BudgetAlertKey("preset-food", BudgetAlertLevel.WARNING),
                BudgetAlertKey("preset-rent", BudgetAlertLevel.EXCEEDED),
                BudgetAlertKey("preset-rent", BudgetAlertLevel.WARNING),
            ),
            store.firedFor(october),
        )
    }

    @Test
    fun `an alert that could not be delivered is not recorded and fires later`() = runTest {
        val store = FakeBudgetAlertStore()

        BudgetAlertTracker(store, FakeBudgetNotifier(delivers = false)) { october }
            .evaluate(listOf(progress("b1", 8_500)))
        assertTrue(store.firedFor(october).isEmpty())

        val delivered = FakeBudgetNotifier()
        tracker(store, delivered).evaluate(listOf(progress("b1", 8_500)))

        assertEquals(listOf(BudgetAlertLevel.WARNING), delivered.alerts.map { it.level })
        assertEquals(1, store.firedFor(october).size)
    }

    @Test
    fun `the notification carries the budget label and amounts`() = runTest {
        val notifier = FakeBudgetNotifier()

        tracker(notifier = notifier).evaluate(listOf(progress("b1", 12_000)))

        val alert: BudgetAlert = notifier.alerts.single()
        assertEquals("Food", alert.label)
        assertEquals(BudgetAlertLevel.EXCEEDED, alert.level)
        assertEquals(Money(10_000), alert.limit)
        assertEquals(Money(12_000), alert.spent)
    }
}
