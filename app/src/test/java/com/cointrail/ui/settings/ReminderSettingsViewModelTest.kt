package com.cointrail.ui.settings

import com.cointrail.data.reminder.ReminderSettings
import com.cointrail.testing.FakeReminderScheduler
import com.cointrail.testing.FakeReminderSettings
import com.cointrail.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class ReminderSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(
        settings: FakeReminderSettings = FakeReminderSettings(),
        scheduler: FakeReminderScheduler = FakeReminderScheduler(),
    ) = ReminderSettingsViewModel(settings, scheduler)

    @Test
    fun `shows the stored reminder time`() {
        val vm = viewModel(FakeReminderSettings(LocalTime.of(7, 5)))

        assertEquals(LocalTime.of(7, 5), vm.state.value.time)
    }

    @Test
    fun `defaults to 21 30 when nothing is configured`() {
        assertEquals(ReminderSettings.DEFAULT_TIME, viewModel().state.value.time)
    }

    @Test
    fun `setting a time persists it and re-arms the schedule`() = runTest(mainDispatcherRule.testDispatcher) {
        val settings = FakeReminderSettings()
        val scheduler = FakeReminderScheduler()
        val vm = viewModel(settings, scheduler)

        vm.setTime(LocalTime.of(20, 15))

        assertEquals(listOf(LocalTime.of(20, 15)), settings.savedTimes)
        assertEquals(LocalTime.of(20, 15), vm.state.value.time)
        assertEquals(1, scheduler.scheduleCount)
    }
}
