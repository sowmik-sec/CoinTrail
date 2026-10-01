package com.cointrail.data.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.cointrail.MainActivity

/** Posts the daily reminder. Wrapped behind an interface so tests never touch Android. */
interface ReminderNotifier {

    /** Posts the nudge, returning false when it could not be shown (e.g. permission not granted). */
    fun notifyReminder(): Boolean
}

class AndroidReminderNotifier(context: Context) : ReminderNotifier {

    private val appContext = context.applicationContext

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily reminder",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "A nightly nudge to log the day's expenses."
        }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun notifyReminder(): Boolean {
        if (!canNotify()) return false

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Log today's expenses")
            .setContentText("Take a moment to add what you spent today.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(quickAddIntent())
            .build()

        NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        return true
    }

    /** Android 13+ needs the runtime notification permission before anything can be posted. */
    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /** Tapping the reminder opens the quick-add screen directly (SPEC §6.2). */
    private fun quickAddIntent(): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_QUICK_ADD, true)
        }
        return PendingIntent.getActivity(
            appContext,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val CHANNEL_ID = "daily_reminder"
        const val NOTIFICATION_ID = 2100
    }
}
