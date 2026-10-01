package com.cointrail.data.reminder

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalTime

/**
 * The user's daily reminder time (SPEC §6.6). Device-local preference, not user data: it never syncs.
 */
interface ReminderSettings {

    fun observeTime(): Flow<LocalTime>

    suspend fun time(): LocalTime

    suspend fun setTime(time: LocalTime)

    /** Whether the Android 13+ notification permission has already been requested once. */
    suspend fun hasRequestedNotificationPermission(): Boolean

    /** Records that the notification permission has been requested, so it is only asked once. */
    suspend fun markNotificationPermissionRequested()

    companion object {
        /** 21:30, the default nudge time (SPEC §6.6). */
        val DEFAULT_TIME: LocalTime = LocalTime.of(21, 30)
    }
}

/** Stores the reminder time as an ISO `HH:mm` string in shared preferences. */
class SharedPreferencesReminderSettings(context: Context) : ReminderSettings {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override fun observeTime(): Flow<LocalTime> = state

    override suspend fun time(): LocalTime = state.value

    override suspend fun setTime(time: LocalTime) {
        prefs.edit().putString(KEY_TIME, time.toString()).apply()
        state.value = time
    }

    override suspend fun hasRequestedNotificationPermission(): Boolean =
        prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)

    override suspend fun markNotificationPermissionRequested() {
        prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()
    }

    private fun read(): LocalTime =
        prefs.getString(KEY_TIME, null)
            ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: ReminderSettings.DEFAULT_TIME

    private companion object {
        const val PREFS_NAME = "cointrail_reminder"
        const val KEY_TIME = "reminder_time"
        const val KEY_PERMISSION_REQUESTED = "notification_permission_requested"
    }
}
