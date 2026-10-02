package com.cointrail.ui.settings

import com.cointrail.data.account.Account
import com.cointrail.data.account.GoogleSignInResult
import com.cointrail.testing.FakeAccountSwitcher
import com.cointrail.testing.FakeGoogleSignIn
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val alice = Account.fromGoogle("alice@example.com", "Alice")

    private fun viewModel(
        accounts: FakeAccountSwitcher = FakeAccountSwitcher(),
        google: FakeGoogleSignIn = FakeGoogleSignIn(),
    ) = AccountSettingsViewModel(accounts, google)

    @Test
    fun `starts signed out and ready to sign in`() {
        val vm = viewModel()

        assertFalse(vm.state.value.signedIn)
        assertTrue(vm.state.value.signInConfigured)
        assertNull(vm.state.value.email)
    }

    @Test
    fun `reports sign-in unavailable when not configured`() {
        val vm = viewModel(google = FakeGoogleSignIn(isConfigured = false))

        assertFalse(vm.state.value.signInConfigured)
    }

    @Test
    fun `successful sign in switches to the account namespace`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts = FakeAccountSwitcher()
            val vm = viewModel(accounts, FakeGoogleSignIn(result = GoogleSignInResult.Success(alice)))

            vm.signIn()

            assertEquals(listOf(alice), accounts.signIns)
            assertTrue(vm.state.value.signedIn)
            assertEquals("alice@example.com", vm.state.value.email)
            assertEquals("Alice", vm.state.value.displayName)
            assertFalse(vm.state.value.busy)
        }

    @Test
    fun `cancelled sign in changes nothing`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts = FakeAccountSwitcher()
            val vm = viewModel(accounts, FakeGoogleSignIn(result = GoogleSignInResult.Cancelled))

            vm.signIn()

            assertTrue(accounts.signIns.isEmpty())
            assertFalse(vm.state.value.signedIn)
            assertNull(vm.state.value.message)
        }

    @Test
    fun `failed sign in surfaces a message`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel(google = FakeGoogleSignIn(result = GoogleSignInResult.Failed("nope")))

            vm.signIn()

            assertEquals("nope", vm.state.value.message)
            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `sign out returns to the local namespace`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts = FakeAccountSwitcher(alice)
            val vm = viewModel(accounts)

            vm.signOut()

            assertEquals(1, accounts.signOutCount)
            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `remove data deletes the account's local copy`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val accounts = FakeAccountSwitcher(alice)
            val vm = viewModel(accounts)

            vm.removeData()

            assertEquals(1, accounts.removeCount)
            assertFalse(vm.state.value.signedIn)
        }

    @Test
    fun `dismissing the message clears it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val vm = viewModel(google = FakeGoogleSignIn(result = GoogleSignInResult.Failed("nope")))
            vm.signIn()

            vm.dismissMessage()

            assertNull(vm.state.value.message)
        }
}
