package com.cointrail.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cointrail.di.AppContainer
import com.cointrail.ui.quickadd.QuickAddRoute
import com.cointrail.ui.today.TodayRoute

/**
 * Root of the two-screen flow for this slice: Today (the everyday screen) and Quick-add. The daily
 * reminder deep-links into quick-add later (Plan 4); for now the FAB is the way in.
 */
@Composable
fun CoinTrailApp(container: AppContainer, modifier: Modifier = Modifier) {
    var showQuickAdd by rememberSaveable { mutableStateOf(false) }

    if (showQuickAdd) {
        BackHandler { showQuickAdd = false }
        QuickAddRoute(
            container = container,
            onDone = { showQuickAdd = false },
            modifier = modifier,
        )
    } else {
        TodayRoute(
            container = container,
            onAddClick = { showQuickAdd = true },
            modifier = modifier,
        )
    }
}
