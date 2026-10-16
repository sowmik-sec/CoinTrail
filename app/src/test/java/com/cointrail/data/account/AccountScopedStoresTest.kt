package com.cointrail.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.repo.ExpenseStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * The account scope, verified at the store seam: two in-memory databases behind a fake session, and
 * one store instance held across a sign-in and a sign-out. An account change must reach what the
 * store already emits, and a write after the switch must land in the newly active scope (ADR-0003).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountScopedStoresTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val localKey = CoinTrailDatabase.LOCAL_ACCOUNT_KEY
    private val alice = Account.fromGoogle("alice@example.com", "Alice")
    private val dayStart = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd = LocalDateTime.of(2026, 10, 6, 0, 0)
    private val noon = LocalDateTime.of(2026, 10, 5, 12, 0)

    /** An account session with no persistence; sign-in/out only move the current-account flow. */
    private class InMemorySession(initial: Account? = null) : AccountSession {
        private val state = MutableStateFlow(initial)
        override val current: StateFlow<Account?> = state
        override suspend fun signIn(account: Account) {
            state.value = account
        }

        override suspend fun signOut() {
            state.value = null
        }
    }

    @Test
    fun `a store held across an account change follows the active scope`() = runBlocking {
        val databases = mutableMapOf<String, CoinTrailDatabase>()
        val manager = AccountManager(
            context,
            InMemorySession(),
            databaseFactory = { key -> databases.getOrPut(key) { CoinTrailDatabase.createInMemory(context) } },
        )
        val expenses: ExpenseStore = AccountScopedExpenseStore(manager)

        expenses.add(Money(100_000), "preset-food", null, null, noon)

        val emissions = Channel<Money>(Channel.UNLIMITED)
        val collector = launch {
            expenses.observeTotalBetween(dayStart, dayEnd).collect { emissions.send(it) }
        }
        assertEquals(Money(100_000), withTimeout(10_000) { emissions.receive() })

        manager.signIn(alice)
        expenses.add(Money(200_000), "preset-food", null, null, noon)
        withTimeout(10_000) {
            while (emissions.receive() != Money(200_000)) {
                // Skip intermediate values until the held store emits the new scope's total.
            }
        }
        assertEquals(
            Money(200_000),
            manager.current.expenses.observeTotalBetween(dayStart, dayEnd).first(),
        )
        assertEquals(
            Money(100_000),
            manager.scope(localKey).expenses.observeTotalBetween(dayStart, dayEnd).first(),
        )

        // Each scope's rows live in the injected in-memory database, and nowhere else.
        assertEquals(2, databases.size)
        assertEquals(
            listOf("preset-food"),
            databases.getValue(localKey).expenseDao().all().map { it.categoryId },
        )

        manager.signOut()
        withTimeout(10_000) {
            while (emissions.receive() != Money(100_000)) {
                // Back on the local scope, the held store resumes emitting the local total.
            }
        }
        assertEquals(
            Money(200_000),
            manager.scope(alice.key).expenses.observeTotalBetween(dayStart, dayEnd).first(),
        )

        collector.cancel()
    }
}
