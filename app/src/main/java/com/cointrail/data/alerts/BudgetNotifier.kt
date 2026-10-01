package com.cointrail.data.alerts

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
import com.cointrail.domain.budget.BudgetAlert
import com.cointrail.domain.budget.BudgetAlertLevel

/** Posts a budget threshold crossing. Wrapped behind an interface so tests never touch Android. */
interface BudgetNotifier {

    /** Posts the alert, returning false when it could not be shown (e.g. permission not granted). */
    fun notify(alert: BudgetAlert): Boolean
}

class AndroidBudgetNotifier(context: Context) : BudgetNotifier {

    private val appContext = context.applicationContext

    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Budget alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Warnings when a monthly budget reaches 80% or 100%."
        }
        appContext.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun notify(alert: BudgetAlert): Boolean {
        if (!canNotify()) return false

        val (title, body) = when (alert.level) {
            BudgetAlertLevel.WARNING -> "Budget warning" to
                "${alert.label} is at ${alert.percent}% of its ${alert.limit.format()} budget."

            BudgetAlertLevel.EXCEEDED -> "Budget exceeded" to
                "${alert.label} is over its ${alert.limit.format()} budget (${alert.spent.format()} spent)."
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(alert))
            .build()

        NotificationManagerCompat.from(appContext).notify(notificationId(alert), notification)
        return true
    }

    /** Android 13+ needs the runtime notification permission before anything can be posted. */
    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openAppIntent(alert: BudgetAlert): PendingIntent {
        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            appContext,
            notificationId(alert),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Stable per budget and threshold, so a re-post replaces rather than stacks. */
    private fun notificationId(alert: BudgetAlert): Int =
        alert.budgetId.hashCode() * 31 + alert.level.ordinal

    private companion object {
        const val CHANNEL_ID = "budget_alerts"
    }
}
