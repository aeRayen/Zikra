package com.zikra.app.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferences
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

sealed interface PrayerTimesUiState {
    data object Loading : PrayerTimesUiState

    /** Nothing saved yet: the screen asks for a location instead of guessing one. */
    data object NoLocation : PrayerTimesUiState

    data class Ready(
        val place: Place?,
        val source: LocationSource,
        val zone: ZoneId,
        /** The place's zone differs from the phone's: the screen says which clock the times use. */
        val zoneDiffersFromDevice: Boolean,
        val date: LocalDate,
        /** Today's six times, in [zone]. */
        val times: List<Pair<PrayerName, LocalTime>>,
        val next: PrayerName,
        val nextTime: LocalTime,
        val nextIsTomorrow: Boolean,
        /** Rounded up: "in 1 minute" until the time is reached, never "in 0 minutes". */
        val minutesUntilNext: Long,
        val method: CalculationMethodId,
        val methodIsAutomatic: Boolean,
        val madhab: AsrMadhab,
    ) : PrayerTimesUiState
}

/**
 * Today's prayer times for the saved location (WP 2.3). Fully offline: the engine runs on the
 * device. Recomputed when the location, the preferences or the language change, and on every [ticker]
 * tick (each minute) so the countdown and the day rollover stay correct.
 */
class PrayerTimesViewModel(
    private val places: () -> PlaceLookup,
    locationStore: LocationStore,
    preferencesStore: PrayerPreferencesStore,
    private val io: CoroutineDispatcher,
    /** Phone time and zone; the zone is only used to tell the user when the place's clock differs. */
    private val clock: () -> Clock = Clock::systemDefaultZone,
    ticker: Flow<Unit> = everyMinute(),
) : ViewModel() {

    private val state = MutableStateFlow<PrayerTimesUiState>(PrayerTimesUiState.Loading)
    val uiState: StateFlow<PrayerTimesUiState> = state.asStateFlow()

    private val language = MutableStateFlow("en")

    init {
        viewModelScope.launch {
            combine(locationStore.location, preferencesStore.preferences, language, ticker) { loc, prefs, lang, _ ->
                Triple(loc, prefs, lang)
            }.collect { (loc, prefs, lang) ->
                state.value = when (loc) {
                    null -> PrayerTimesUiState.NoLocation
                    else -> withContext(io) { ready(loc, prefs, lang) }
                }
            }
        }
    }

    fun setLanguage(tag: String) {
        language.value = tag
    }

    private fun ready(saved: SavedLocation, prefs: PrayerPreferences, lang: String): PrayerTimesUiState.Ready {
        val phone = clock()
        val ctx = PrayerContext.of(saved, prefs, places(), lang, phone.zone)
        val (place, point, zone) = Triple(ctx.place, ctx.point, ctx.zone)
        val resolved = ctx.resolved
        val now = phone.instant()
        val today = now.atZone(zone).toLocalDate()
        val day = PrayerCalculator.compute(point, today, zone, resolved.settings)
        val next = PrayerCalculator.next(point, now, zone, resolved.settings)
        val untilNext = Duration.between(now, next.at)
        return PrayerTimesUiState.Ready(
            place = place,
            source = saved.source,
            zone = zone,
            zoneDiffersFromDevice = zone.rules.getOffset(now) != phone.zone.rules.getOffset(now),
            date = today,
            times = PrayerName.entries.map { it to day[it].atZone(zone).toLocalTime() },
            next = next.prayer,
            nextTime = next.at.atZone(zone).toLocalTime(),
            nextIsTomorrow = next.at.atZone(zone).toLocalDate() != today,
            minutesUntilNext = (untilNext.seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE,
            method = resolved.settings.method,
            methodIsAutomatic = resolved.methodIsAutomatic,
            madhab = resolved.settings.madhab,
        )
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60L
        private const val MS_PER_MINUTE = 60_000L

        /** Emits now, then at the start of every minute. */
        fun everyMinute(now: () -> Long = System::currentTimeMillis): Flow<Unit> = flow {
            while (true) {
                emit(Unit)
                delay(MS_PER_MINUTE - now() % MS_PER_MINUTE)
            }
        }
    }
}
