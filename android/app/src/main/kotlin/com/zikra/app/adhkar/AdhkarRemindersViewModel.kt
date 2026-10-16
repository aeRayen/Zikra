package com.zikra.app.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.settings.AdhkarReminders
import com.zikra.core.settings.AdhkarRemindersStore
import com.zikra.core.settings.LocationStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

data class AdhkarRemindersUiState(
    val loaded: Boolean = false,
    val reminders: AdhkarReminders = AdhkarReminders(),
    /** Morning and evening need the prayer times of a saved location. */
    val hasLocation: Boolean = false,
)

/** Turns the adhkar reminders on and off (WP 2.14c); the alarms follow the store (AdhkarReminderAlarms). */
class AdhkarRemindersViewModel(private val store: AdhkarRemindersStore, location: LocationStore) : ViewModel() {

    val uiState: StateFlow<AdhkarRemindersUiState> =
        combine(store.data, location.location) { r, l -> AdhkarRemindersUiState(true, r, l != null) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, AdhkarRemindersUiState())

    private val failed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    fun setMorning(on: Boolean) = edit { it.copy(morning = on) }

    fun setEvening(on: Boolean) = edit { it.copy(evening = on) }

    fun setSleep(on: Boolean) = edit { it.copy(sleep = on) }

    /** Choosing a time also turns the sleep reminder on: that is why it was chosen. */
    fun setSleepTime(hour: Int, minute: Int) = edit {
        it.copy(sleep = true, sleepMinutes = hour * MINUTES_PER_HOUR + minute)
    }

    private fun edit(transform: (AdhkarReminders) -> AdhkarReminders) {
        viewModelScope.launch {
            failed.value = try {
                store.update(transform)
                false
            } catch (_: IOException) {
                true
            }
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
    }
}
