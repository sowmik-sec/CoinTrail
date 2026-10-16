package com.cointrail

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.cointrail.di.AppContainer
import com.cointrail.ui.CoinTrailApp
import com.cointrail.ui.theme.CoinTrailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** Set when the activity is launched or re-focused from the daily reminder notification. */
    private val openQuickAdd = mutableStateOf(false)

    private val notificationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars on every API level (Android 15 enforces it anyway), so Home's
        // hero reaches the top edge and each screen pads its own insets.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as CoinTrailApplication).container
        // Seed, generate due recurring expenses, then sync — at start and again whenever the active
        // account changes (SPEC §7), so a freshly switched namespace is usable without a restart.
        // Sequentially, so the journal snapshot includes the generated occurrences instead of
        // racing them.
        lifecycleScope.launch {
            container.accounts.accountKey.collect {
                container.seedDefaults()
                container.recurringGenerator.generateDue()
                container.sync.syncNow()
            }
        }
        lifecycleScope.launch { container.recurringScheduler.enqueuePeriodic() }
        // Keeps the daily reminder armed; also re-establishes the chain after a device restart.
        lifecycleScope.launch { container.reminderScheduler.scheduleNext() }
        lifecycleScope.launch { container.syncScheduler.enqueuePeriodic() }
        // Keeps the weekly Drive snapshot armed; a no-op until an account is signed in.
        lifecycleScope.launch { container.backupScheduler.enqueuePeriodic() }
        // An account change reaches the screens through the account-scoped stores themselves
        // (ADR-0003); the activity never restarts for one.
        openQuickAdd.value = intent.wantsQuickAdd()
        setContent {
            CoinTrailTheme {
                CoinTrailApp(
                    container = container,
                    startInQuickAdd = openQuickAdd.value,
                    onStartInQuickAddConsumed = { openQuickAdd.value = false },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        requestNotificationPermissionOnce()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.wantsQuickAdd()) openQuickAdd.value = true
    }

    /** The reminder is on by default, so Android 13+ users are asked for POST_NOTIFICATIONS once. */
    private fun requestNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return

        val container: AppContainer = (application as CoinTrailApplication).container
        lifecycleScope.launch {
            if (container.reminderSettings.hasRequestedNotificationPermission()) return@launch
            container.reminderSettings.markNotificationPermissionRequested()
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun Intent.wantsQuickAdd(): Boolean = getBooleanExtra(EXTRA_OPEN_QUICK_ADD, false)

    companion object {
        /** Extra on the reminder notification's intent: open straight into quick-add (SPEC §6.6). */
        const val EXTRA_OPEN_QUICK_ADD: String = "com.cointrail.extra.OPEN_QUICK_ADD"
    }
}
