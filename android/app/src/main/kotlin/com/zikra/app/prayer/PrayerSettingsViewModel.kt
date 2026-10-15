package com.zikra.app.prayer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.HighLatitudeRule
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import com.zikra.core.prayer.RegionalDefaults
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferences
import com.zikra.core.settings.PrayerPreferencesStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

data class PrayerSettingsUiState(
    val loaded: Boolean = false,
    /** null = automatic. */
    val method: CalculationMethodId? = null,
    val madhab: AsrMadhab? = null,
    val highLatitudeRule: HighLatitudeRule? = null,
    /** Per prayer, 0 when not adjusted. */
    val adjustments: Map<PrayerName, Int> = emptyMap(),
    /** What «automatic» means for the saved location, shown next to the automatic option. */
    val automaticMethod: CalculationMethodId = RegionalDefaults.method(null),
    val automaticMadhab: AsrMadhab = RegionalDefaults.madhab(null),
    /** The last change could not be written (e.g. storage full); the screen says so. */
    val saveFailed: Boolean = false,
)

/** Prayer settings (WP 2.3): method, Asr madhab, high-latitude rule and per-prayer adjustments. */
class PrayerSettingsViewModel(
    private val places: () -> PlaceLookup,
    locationStore: LocationStore,
    private val store: PrayerPreferencesStore,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val state = MutableStateFlow(PrayerSettingsUiState())
    val uiState: StateFlow<PrayerSettingsUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(store.preferences, locationStore.location) { prefs, loc -> prefs to loc }.collect { (prefs, loc) ->
                // Country code does not depend on the language: any name language works here.
                val country = loc?.cityId?.let { id -> withContext(io) { places().byId(id, "en")?.countryCode } }
                state.value = PrayerSettingsUiState(
                    saveFailed = state.value.saveFailed,
                    loaded = true,
                    method = CalculationMethodId.fromId(prefs.methodId),
                    madhab = AsrMadhab.entries.firstOrNull { it.name == prefs.madhab },
                    highLatitudeRule = HighLatitudeRule.entries.firstOrNull { it.name == prefs.highLatitudeRule },
                    adjustments = PrayerName.entries.associateWith { prefs.adjustmentMinutes[it.name] ?: 0 },
                    automaticMethod = RegionalDefaults.method(country),
                    automaticMadhab = RegionalDefaults.madhab(country),
                )
            }
        }
    }

    fun setMethod(method: CalculationMethodId?) = edit { it.copy(methodId = method?.id) }

    fun setMadhab(madhab: AsrMadhab?) = edit { it.copy(madhab = madhab?.name) }

    fun setHighLatitudeRule(rule: HighLatitudeRule?) = edit { it.copy(highLatitudeRule = rule?.name) }

    /** Moves one prayer by [deltaMinutes], kept within ±30; zero removes the entry. */
    fun adjust(prayer: PrayerName, deltaMinutes: Int) = edit { prefs ->
        val now = prefs.adjustmentMinutes[prayer.name] ?: 0
        val next = (now + deltaMinutes).coerceIn(-PrayerSettings.MAX_ADJUSTMENT, PrayerSettings.MAX_ADJUSTMENT)
        val current = prefs.adjustmentMinutes
        val map = if (next == 0) current - prayer.name else current + (prayer.name to next)
        prefs.copy(adjustmentMinutes = map)
    }

    /** Back to automatic method and madhab, engine high-latitude rule, no adjustments. */
    fun reset() = edit { PrayerPreferences() }

    private fun edit(transform: (PrayerPreferences) -> PrayerPreferences) {
        viewModelScope.launch {
            try {
                store.update(transform)
                state.update { it.copy(saveFailed = false) }
            } catch (_: IOException) {
                state.update { it.copy(saveFailed = true) } // never crash on a failed write
            }
        }
    }
}
