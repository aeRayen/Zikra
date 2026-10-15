package com.zikra.app.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The saved location, with its city resolved in the current language (null if none / not found). */
data class CurrentLocation(val source: LocationSource, val place: Place?, val latitude: Double, val longitude: Double)

enum class LocateStatus { IDLE, LOCATING, PERMISSION_DENIED, UNAVAILABLE }

data class LocationUiState(
    /** null while loading or when nothing is saved yet; see [loaded]. */
    val current: CurrentLocation? = null,
    val loaded: Boolean = false,
    val query: String = "",
    val results: List<Place> = emptyList(),
    val searching: Boolean = false,
    val status: LocateStatus = LocateStatus.IDLE,
)

/**
 * WP 2.1: choose the location for prayer times by searching a city (no permission needed) or from the
 * device's approximate location. Refusing the permission never blocks the user: search always works.
 */
class LocationViewModel(
    private val places: () -> PlaceLookup,
    private val store: LocationStore,
    private val locator: DeviceLocator,
    private val io: CoroutineDispatcher,
    private val systemZone: () -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val state = MutableStateFlow(LocationUiState())
    val uiState: StateFlow<LocationUiState> = state.asStateFlow()

    private val language = MutableStateFlow("en")
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            combine(store.location, language) { saved, lang -> saved to lang }.collect { (saved, lang) ->
                val current = saved?.let {
                    val place = it.cityId?.let { id -> withContext(io) { places().byId(id, lang) } }
                    CurrentLocation(it.source, place, it.latitude, it.longitude)
                }
                state.update { s -> s.copy(current = current, loaded = true) }
            }
        }
    }

    /** UI language (BCP 47 tag); names are re-resolved when it changes. */
    fun setLanguage(tag: String) {
        if (language.value == tag) return
        language.value = tag
        if (state.value.query.isNotBlank()) onQueryChange(state.value.query)
    }

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            state.update { it.copy(searching = true) }
            delay(SEARCH_DEBOUNCE_MS)
            val results = withContext(io) { places().search(query, language.value, MAX_RESULTS) }
            state.update { it.copy(results = results, searching = false) }
        }
    }

    fun onPick(place: Place) {
        searchJob?.cancel()
        state.update { it.copy(query = "", results = emptyList(), searching = false, status = LocateStatus.IDLE) }
        viewModelScope.launch {
            store.save(
                SavedLocation(
                    LocationSource.CITY,
                    place.cityId,
                    place.latitude,
                    place.longitude,
                    place.timeZone.id,
                    clock()
                ),
            )
        }
    }

    /** Result of the system permission dialog (or true when the permission was already granted). */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!granted) {
            state.update { it.copy(status = LocateStatus.PERMISSION_DENIED) }
            return
        }
        viewModelScope.launch {
            state.update { it.copy(status = LocateStatus.LOCATING) }
            val status = when (val result = locator.approximateLocation()) {
                is LocateResult.Found -> {
                    saveDeviceLocation(result)
                    LocateStatus.IDLE
                }
                LocateResult.NoPermission -> LocateStatus.PERMISSION_DENIED
                LocateResult.Unavailable -> LocateStatus.UNAVAILABLE
            }
            state.update { it.copy(status = status) }
        }
    }

    private suspend fun saveDeviceLocation(fix: LocateResult.Found) {
        val nearest = withContext(io) { places().nearest(fix.latitude, fix.longitude, language.value) }
        // The nearest city's zone matches the place; without a city, the phone's own zone is the best guess.
        val zone = nearest?.place?.timeZone?.id ?: systemZone()
        store.save(
            SavedLocation(LocationSource.DEVICE, nearest?.place?.cityId, fix.latitude, fix.longitude, zone, clock())
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 150L
        const val MAX_RESULTS = 30
    }
}
