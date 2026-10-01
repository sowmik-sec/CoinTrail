package com.cointrail.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cointrail.data.reminder.ReminderScheduler
import com.cointrail.data.reminder.ReminderSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

data class ReminderSettingsUiState(
    val time: LocalTime = ReminderSettings.DEFAULT_TIME,
)

/** Backs the daily reminder settings screen: shows the configured time and re-arms the schedule. */
class ReminderSettingsViewModel(
    private val settings: ReminderSettings,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    val state: StateFlow<ReminderSettingsUiState> =
        settings.observeTime()
            .map { ReminderSettingsUiState(time = it) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ReminderSettingsUiState())

    /** Saves a new reminder time and reschedules the next nudge to match it. */
    fun setTime(time: LocalTime) {
        viewModelScope.launch {
            settings.setTime(time)
            scheduler.scheduleNext()
        }
    }
}
