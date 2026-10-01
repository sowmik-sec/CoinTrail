package com.cointrail.di

import android.content.Context
import com.cointrail.data.alerts.AndroidBudgetNotifier
import com.cointrail.data.alerts.BudgetAlertTracker
import com.cointrail.data.alerts.SharedPreferencesBudgetAlertStore
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.repo.BudgetRepository
import com.cointrail.data.repo.CategoryRepository
import com.cointrail.data.repo.ExpenseRepository
import com.cointrail.data.repo.PaymentMethodRepository

/**
 * Hand-rolled dependency graph for the single-module app. Holds the local database and the
 * repositories the screens talk to. Defaults to the "local" account namespace until Google
 * sign-in lands (SPEC §7).
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext
    private val database: CoinTrailDatabase = CoinTrailDatabase.create(appContext)

    val expenses: ExpenseRepository = ExpenseRepository(database.expenseDao())
    val categories: CategoryRepository = CategoryRepository(database.categoryDao())
    val paymentMethods: PaymentMethodRepository = PaymentMethodRepository(database.paymentMethodDao())
    val budgets: BudgetRepository = BudgetRepository(database.budgetDao())

    /** Raises the 80%/100% budget notifications, at most once per budget per month (SPEC §6.5). */
    val budgetAlerts: BudgetAlertTracker = BudgetAlertTracker(
        store = SharedPreferencesBudgetAlertStore(appContext),
        notifier = AndroidBudgetNotifier(appContext),
    )

    /** Seeds the preset categories and payment methods exactly once. */
    suspend fun seedDefaults() {
        categories.ensureSeeded()
        paymentMethods.ensureSeeded()
    }
}
