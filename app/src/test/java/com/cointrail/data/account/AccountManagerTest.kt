package com.cointrail.data.account

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.cointrail.core.Money
import com.cointrail.data.db.CoinTrailDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dayStart = LocalDateTime.of(2026, 10, 5, 0, 0)
    private val dayEnd = LocalDateTime.of(2026, 10, 6, 0, 0)
    private val alice = Account.fromGoogle("alice@example.com", "Alice")
    private val bob = Account.fromGoogle("bob@example.com", "Bob")

    private fun manager() = AccountManager(context, SharedPreferencesAccountSession(context))

    private suspend fun AccountData.logExpense(paisa: Long) {
        expenses.add(Money(paisa), "preset-food", null, null, LocalDateTime.of(2026, 10, 5, 12, 0))
    }

    private suspend fun AccountData.total(): Money =
        expenses.observeTotalBetween(dayStart, dayEnd).first()

    @Test
    fun `starts in the local namespace with no account`() = runTest {
        val manager = manager()

        assertEquals("local", manager.accountKey.value)
        assertEquals("local", manager.current.key)
        assertNull(manager.account.value)
    }

    @Test
    fun `signing in switches to the account namespace`() = runTest {
        val manager = manager()

        manager.signIn(alice)

        assertEquals("alice@example.com", manager.accountKey.value)
        assertEquals("alice@example.com", manager.current.key)
        assertEquals(alice, manager.account.value)
    }

    @Test
    fun `signing out hides the data but keeps the local copy`() = runTest {
        val manager = manager()
        manager.signIn(alice)
        manager.current.logExpense(125_000)

        manager.signOut()
        assertEquals("local", manager.accountKey.value)
        assertEquals(Money.ZERO, manager.current.total())

        manager.signIn(alice)
        assertEquals(Money(125_000), manager.current.total())
    }

    @Test
    fun `two accounts never see each other's data`() = runTest {
        val manager = manager()
        manager.signIn(alice)
        manager.current.logExpense(100_000)
        manager.signOut()

        manager.signIn(bob)
        assertEquals(Money.ZERO, manager.current.total())
        manager.current.logExpense(200_000)
        assertEquals(Money(200_000), manager.current.total())
    }

    @Test
    fun `remove my data deletes the namespace file and returns to local`() = runTest {
        val manager = manager()
        manager.signIn(alice)
        manager.current.logExpense(50_000)
        val aliceFile = context.getDatabasePath(CoinTrailDatabase.databaseName("alice@example.com"))
        assertTrue(aliceFile.exists())

        manager.removeSignedInData()

        assertEquals("local", manager.accountKey.value)
        assertNull(manager.account.value)
        assertFalse(aliceFile.exists())
    }

    @Test
    fun `removed account comes back empty when signed in again`() = runTest {
        val manager = manager()
        manager.signIn(alice)
        manager.current.logExpense(50_000)
        manager.removeSignedInData()

        manager.signIn(alice)

        assertEquals(Money.ZERO, manager.current.total())
    }

    @Test
    fun `removing when already signed out is a safe no-op`() = runTest {
        val manager = manager()

        manager.removeSignedInData()

        assertEquals("local", manager.accountKey.value)
        assertNull(manager.account.value)
    }
}
