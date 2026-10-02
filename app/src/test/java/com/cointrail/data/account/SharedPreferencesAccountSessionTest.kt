package com.cointrail.data.account

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesAccountSessionTest {

    private fun session() =
        SharedPreferencesAccountSession(ApplicationProvider.getApplicationContext())

    private val alice = Account.fromGoogle("Alice@Example.com", "Alice")

    @Test
    fun `starts signed out in the local namespace`() = runTest {
        val session = session()

        assertNull(session.current.value)
        assertEquals("local", AccountSession.namespaceKey(session.current.value))
    }

    @Test
    fun `sign in remembers the account and its namespace key`() = runTest {
        val session = session()

        session.signIn(alice)

        assertEquals(alice, session.current.value)
        assertEquals("alice@example.com", AccountSession.namespaceKey(session.current.value))
    }

    @Test
    fun `sign out clears the account`() = runTest {
        val session = session()
        session.signIn(alice)

        session.signOut()

        assertNull(session.current.value)
    }

    @Test
    fun `a signed-in account survives a fresh instance`() = runTest {
        session().signIn(alice)

        assertEquals(alice, session().current.value)
    }

    @Test
    fun `email is normalized into the namespace key`() {
        assertEquals("alice@example.com", alice.key)
        assertEquals("Alice@Example.com", alice.email)
    }
}
