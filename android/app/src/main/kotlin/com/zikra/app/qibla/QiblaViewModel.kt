package com.zikra.app.qibla

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface QiblaUiState {
    data object Loading : QiblaUiState

    data object NoLocation : QiblaUiState

    data class Ready(
        val place: Place?,
        val source: LocationSource,
        /** Qibla bearing, degrees clockwise from TRUE north (great circle to the Kaaba). */
        val qiblaTrue: Double,
        val hasCompass: Boolean,
        /** Phone heading from TRUE north (magnetic + declination); null until the first reading. */
        val headingTrue: Double? = null,
        val aligned: Boolean = false,
        val needsCalibration: Boolean = false,
        val interference: Boolean = false,
    ) : QiblaUiState
}

/**
 * Qibla direction (WP 2.6). The bearing comes from the location alone and is always shown; the live
 * compass is an aid on top of it, with honest warnings when it cannot be trusted.
 */
private val UNTRUSTED = setOf(CompassAccuracy.LOW, CompassAccuracy.UNRELIABLE)

class QiblaViewModel(
    private val places: () -> PlaceLookup,
    private val location: LocationStore,
    private val compass: Compass,
    private val io: CoroutineDispatcher,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val state = MutableStateFlow<QiblaUiState>(QiblaUiState.Loading)
    val uiState: StateFlow<QiblaUiState> = state.asStateFlow()

    private var declination = 0.0
    private var compassJob: Job? = null
    private var smoothed: Double? = null

    // Declared before init: init collects it.
    private val language = MutableStateFlow("en")

    init {
        viewModelScope.launch {
            combine(location.location, language) { saved, lang -> saved to lang }.collect { (saved, lang) ->
                state.value = if (saved == null) {
                    QiblaUiState.NoLocation
                } else {
                    val place = saved.cityId?.let { id -> withContext(io) { places().byId(id, lang) } }
                    declination = CompassMath.declination(saved.latitude, saved.longitude, now())
                    val previous = state.value as? QiblaUiState.Ready
                    QiblaUiState.Ready(
                        place = place,
                        source = saved.source,
                        qiblaTrue = PrayerCalculator.qiblaBearing(GeoPoint(saved.latitude, saved.longitude)),
                        hasCompass = compass.available,
                        // Keep the live compass values while only the language changed.
                        headingTrue = previous?.headingTrue,
                    )
                }
            }
        }
    }

    fun setLanguage(tag: String) {
        language.value = tag
    }

    /** Call while the screen is visible (sensors cost battery); [stopCompass] when it is not. */
    fun startCompass(displayRotation: () -> Int) {
        if (!compass.available || compassJob?.isActive == true) return
        compassJob = viewModelScope.launch {
            compass.readings(displayRotation).collect { reading -> onReading(reading) }
        }
    }

    fun stopCompass() {
        compassJob?.cancel()
        compassJob = null
    }

    private fun onReading(reading: CompassReading) {
        val heading = CompassMath.smooth(smoothed, CompassMath.normalize(reading.magneticAzimuth + declination))
        smoothed = heading
        state.update { current ->
            if (current !is QiblaUiState.Ready) return@update current
            current.copy(
                headingTrue = heading,
                aligned = CompassMath.isAligned(current.qiblaTrue, heading),
                needsCalibration = reading.accuracy in UNTRUSTED,
                interference = reading.fieldMicroTesla?.let { it !in CompassMath.NORMAL_FIELD_MICROTESLA } ?: false,
            )
        }
    }
}
