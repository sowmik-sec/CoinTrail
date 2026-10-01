package com.cointrail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.cointrail.ui.CoinTrailApp
import com.cointrail.ui.theme.CoinTrailTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as CoinTrailApplication).container
        lifecycleScope.launch { container.seedDefaults() }
        setContent {
            CoinTrailTheme {
                CoinTrailApp(container = container)
            }
        }
    }
}
