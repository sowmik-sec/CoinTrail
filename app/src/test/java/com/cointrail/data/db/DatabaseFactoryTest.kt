package com.cointrail.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseFactoryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ts: LocalDateTime = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Test
    fun `default account key is local`() {
        assertEquals("local", CoinTrailDatabase.LOCAL_ACCOUNT_KEY)
    }

    @Test
    fun `database name is deterministic and key specific`() {
        assertEquals(CoinTrailDatabase.databaseName("local"), CoinTrailDatabase.databaseName("local"))
        assertNotEquals(CoinTrailDatabase.databaseName("local"), CoinTrailDatabase.databaseName("alice@example.com"))
    }

    @Test
    fun `account keys that sanitize alike still resolve to distinct files`() {
        assertNotEquals(
            CoinTrailDatabase.databaseName("a.b@x.com"),
            CoinTrailDatabase.databaseName("a_b@x.com"),
        )
    }

    @Test
    fun `create defaults to the local namespace and isolates account data`() {
        val local = CoinTrailDatabase.create(context)
        val other = CoinTrailDatabase.create(context, "alice@example.com")
        try {
            runBlocking {
                local.categoryDao().upsert(CategoryEntity("c1", "Food", true, false, 0, ts))
            }
            assertTrue(context.getDatabasePath(CoinTrailDatabase.databaseName("local")).exists())
            assertEquals(1, runBlocking { local.categoryDao().count() })
            assertEquals(0, runBlocking { other.categoryDao().count() })
        } finally {
            local.close()
            other.close()
        }
    }
}
