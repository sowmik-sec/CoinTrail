package com.cointrail.data.reminder

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SharedPreferencesReminderSettingsTest {

    private fun settings() =
        SharedPreferencesReminderSettings(ApplicationProvider.getApplicationContext())

    @Test
    fun `defaults to 21 30 when nothing is stored`() = runTest {
        assertEquals(LocalTime.of(21, 30), settings().time())
    }

    @Test
    fun `stores and reads back the reminder time`() = runTest {
        val settings = settings()

        settings.setTime(LocalTime.of(7, 45))

        assertEquals(LocalTime.of(7, 45), settings.time())
    }

    @Test
    fun `observing the time reflects updates`() = runTest {
        val settings = settings()

        settings.setTime(LocalTime.of(6, 0))

        assertEquals(LocalTime.of(6, 0), settings.observeTime().first())
    }

    @Test
    fun `a saved time survives a fresh instance`() = runTest {
        settings().setTime(LocalTime.of(8, 15))

        assertEquals(LocalTime.of(8, 15), settings().time())
    }

    @Test
    fun `the notification permission has not been requested to begin with`() = runTest {
        assertFalse(settings().hasRequestedNotificationPermission())
    }

    @Test
    fun `marking the permission request is remembered`() = runTest {
        val settings = settings()

        settings.markNotificationPermissionRequested()

        assertTrue(settings.hasRequestedNotificationPermission())
    }
}
