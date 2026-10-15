package com.zikra.app.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferencesStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

data class MonthDay(val date: LocalDate, val times: List<LocalTime>)

sealed interface PrayerMonthUiState {
    data object Loading : PrayerMonthUiState

    data object NoLocation : PrayerMonthUiState

    data class Ready(
        val month: YearMonth,
        /** Today in the place's zone; highlighted when it falls in [month]. */
        val today: LocalDate,
        /** One row per day; times in [PrayerName.entries] order, in the place's zone. */
        val days: List<MonthDay>,
    ) : PrayerMonthUiState
}

/** Monthly timetable (WP 2.3), computed offline for the saved location and preferences. */
class PrayerMonthViewModel(
    private val places: () -> PlaceLookup,
    locationStore: LocationStore,
    preferencesStore: PrayerPreferencesStore,
    private val io: CoroutineDispatcher,
    private val clock: () -> Clock = Clock::systemDefaultZone,
) : ViewModel() {

    private val state = MutableStateFlow<PrayerMonthUiState>(PrayerMonthUiState.Loading)
    val uiState: StateFlow<PrayerMonthUiState> = state.asStateFlow()

    /** null = the current month in the place's zone (resolved once the location is known). */
    private val shownMonth = MutableStateFlow<YearMonth?>(null)

    init {
        viewModelScope.launch {
            combine(locationStore.location, preferencesStore.preferences, shownMonth) { loc, prefs, month ->
                Triple(loc, prefs, month)
            }.collect { (loc, prefs, month) ->
                state.value = if (loc == null) {
                    PrayerMonthUiState.NoLocation
                } else {
                    withContext(io) {
                        val phone = clock()
                        val ctx = PrayerContext.of(loc, prefs, places(), "en", phone.zone)
                        val today = phone.instant().atZone(ctx.zone).toLocalDate()
                        val shown = month ?: YearMonth.from(today)
                        val days = (1..shown.lengthOfMonth()).map { d ->
                            val date = shown.atDay(d)
                            val day = PrayerCalculator.compute(ctx.point, date, ctx.zone, ctx.resolved.settings)
                            MonthDay(date, PrayerName.entries.map { day[it].atZone(ctx.zone).toLocalTime() })
                        }
                        PrayerMonthUiState.Ready(shown, today, days)
                    }
                }
            }
        }
    }

    fun previousMonth() = shift(-1)

    fun nextMonth() = shift(1)

    private fun shift(months: Long) {
        val current = (state.value as? PrayerMonthUiState.Ready)?.month ?: return
        shownMonth.value = current.plusMonths(months)
    }
}
