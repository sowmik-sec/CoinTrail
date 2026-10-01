package com.cointrail.di

import android.content.Context
import com.cointrail.data.db.CoinTrailDatabase
import com.cointrail.data.repo.CategoryRepository
import com.cointrail.data.repo.ExpenseRepository
import com.cointrail.data.repo.PaymentMethodRepository

/**
 * Hand-rolled dependency graph for the single-module app. Holds the local database and the
 * repositories the screens talk to. Defaults to the "local" account namespace until Google
 * sign-in lands (SPEC §7).
 */
class AppContainer(context: Context) {

    private val database: CoinTrailDatabase = CoinTrailDatabase.create(context.applicationContext)

    val expenses: ExpenseRepository = ExpenseRepository(database.expenseDao())
    val categories: CategoryRepository = CategoryRepository(database.categoryDao())
    val paymentMethods: PaymentMethodRepository = PaymentMethodRepository(database.paymentMethodDao())

    /** Seeds the preset categories and payment methods exactly once. */
    suspend fun seedDefaults() {
        categories.ensureSeeded()
        paymentMethods.ensureSeeded()
    }
}
