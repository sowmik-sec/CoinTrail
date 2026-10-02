package com.cointrail.di

import android.content.Context
import com.cointrail.BuildConfig
import com.cointrail.data.account.AccountManager
import com.cointrail.data.account.AccountSession
import com.cointrail.data.account.CredentialManagerGoogleSignIn
import com.cointrail.data.account.GoogleSignIn
import com.cointrail.data.account.SharedPreferencesAccountSession
import com.cointrail.data.alerts.AndroidBudgetNotifier
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.data.alerts.SharedPreferencesBudgetAlertStore
import com.cointrail.data.backup.BackupManager
import com.cointrail.data.backup.BackupScheduler
import com.cointrail.data.backup.WorkManagerBackupScheduler
import com.cointrail.data.backup.drive.GoogleDriveSnapshotRemoteStore
import com.cointrail.data.recurring.RecurringExpenseGenerator
import com.cointrail.data.recurring.RecurringGenerationScheduler
import com.cointrail.data.recurring.WorkManagerRecurringGenerationScheduler
import com.cointrail.data.reminder.AndroidReminderNotifier
import com.cointrail.data.reminder.ReminderNotifier
import com.cointrail.data.reminder.ReminderScheduler
import com.cointrail.data.reminder.ReminderSettings
import com.cointrail.data.reminder.SharedPreferencesReminderSettings
import com.cointrail.data.reminder.WorkManagerReminderScheduler
import com.cointrail.data.repo.BudgetRepository
import com.cointrail.data.repo.CategoryRepository
import com.cointrail.data.repo.ExpenseRepository
import com.cointrail.data.repo.PaymentMethodRepository
import com.cointrail.data.repo.RecurringSeriesRepository
import com.cointrail.data.sync.SharedPreferencesSyncSettings
import com.cointrail.data.sync.SyncManager
import com.cointrail.data.sync.SyncScheduler
import com.cointrail.data.sync.SyncSettings
import com.cointrail.data.sync.WorkManagerSyncScheduler
import com.cointrail.data.sync.drive.GoogleDriveAccessTokens
import com.cointrail.data.sync.drive.GoogleDriveSyncRemoteStore

/**
 * Hand-rolled dependency graph for the single-module app. The user-facing repositories are read
 * through [accounts], so every screen and background job automatically follows whichever account is
 * signed in ("local" when signed out), per SPEC §7.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    private val accountSession: AccountSession = SharedPreferencesAccountSession(appContext)

    /** Which account is active, and how to sign in/out or remove its on-device data (SPEC §7). */
    val accounts: AccountManager = AccountManager(appContext, accountSession)

    /**
     * Optional Google sign-in; disabled (but harmless) until a client ID is configured. Takes the
     * calling Activity's context because Credential Manager shows its picker from it.
     */
    fun googleSignIn(activityContext: Context): GoogleSignIn =
        CredentialManagerGoogleSignIn(activityContext, BuildConfig.GOOGLE_WEB_CLIENT_ID)

    val expenses: ExpenseRepository get() = accounts.current.expenses
    val categories: CategoryRepository get() = accounts.current.categories
    val paymentMethods: PaymentMethodRepository get() = accounts.current.paymentMethods
    val budgets: BudgetRepository get() = accounts.current.budgets
    val recurring: RecurringSeriesRepository get() = accounts.current.recurring

    /** Creates the ordinary expenses for due recurring series, on app open and from the background job. */
    val recurringGenerator: RecurringExpenseGenerator get() = accounts.current.recurringGenerator
    val recurringScheduler: RecurringGenerationScheduler =
        WorkManagerRecurringGenerationScheduler(appContext)

    /** Device-local sync bookkeeping and the one Drive sync cycle for the signed-in account (SPEC §7). */
    val syncSettings: SyncSettings = SharedPreferencesSyncSettings(appContext)
    val sync: SyncManager = SyncManager(
        account = accounts.account,
        localStore = { accounts.current.syncStore },
        remoteStore = { account -> GoogleDriveSyncRemoteStore(GoogleDriveAccessTokens(appContext, account)) },
        settings = syncSettings,
    )
    val syncScheduler: SyncScheduler = WorkManagerSyncScheduler(appContext)

    /**
     * Snapshots, restore and JSON export/import (SPEC §7). Snapshots live in the same Drive app
     * folder as the sync journal, keyed to the same signed-in account; JSON export/import works in
     * any namespace, account or not.
     */
    val backup: BackupManager = BackupManager(
        account = accounts.account,
        localStore = { accounts.current.syncStore },
        snapshotStore = { account -> GoogleDriveSnapshotRemoteStore(GoogleDriveAccessTokens(appContext, account)) },
    )
    val backupScheduler: BackupScheduler = WorkManagerBackupScheduler(appContext)

    /** Raises the 80%/100% budget notifications, at most once per budget per month (SPEC §6.5). */
    val budgetAlerts: BudgetAlertTracker = BudgetAlertTracker(
        store = SharedPreferencesBudgetAlertStore(appContext),
        notifier = AndroidBudgetNotifier(appContext),
    )

    /** The daily nudge: its configured time, its notification, and the WorkManager job (SPEC §6.6). */
    val reminderSettings: ReminderSettings = SharedPreferencesReminderSettings(appContext)
    val reminderNotifier: ReminderNotifier = AndroidReminderNotifier(appContext)
    val reminderScheduler: ReminderScheduler = WorkManagerReminderScheduler(appContext, reminderSettings)

    /** Seeds the active namespace's preset categories and payment methods exactly once. */
    suspend fun seedDefaults() {
        accounts.current.seedDefaults()
    }
}
