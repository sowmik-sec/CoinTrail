package com.cointrail

import android.app.Application
import com.cointrail.di.AppContainer

/**
 * Owns the app-wide dependency graph. Preset seeding is triggered by the UI entry point
 * ([MainActivity]) rather than here, so data-layer tests (which Robolectric runs under a real
 * Application) are not polluted by startup writes.
 */
class CoinTrailApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
