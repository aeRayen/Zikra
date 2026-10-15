package com.zikra.app.qibla

import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QiblaViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val cairo = Place(360630, "Cairo", null, "Egypt", "EG", 30.06263, 31.24967, ZoneId.of("Africa/Cairo"))
    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = cairo.takeIf { it.cityId == cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }

    private class FakeCompass(override val available: Boolean) : Compass {
        val readings = MutableSharedFlow<CompassReading>(extraBufferCapacity = 8)
        override fun readings(displayRotation: () -> Int): Flow<CompassReading> = readings
    }

    private val nowMs = java.time.Instant.parse("2026-06-01T00:00:00Z").toEpochMilli()
    private val declination get() = CompassMath.declination(cairo.latitude, cairo.longitude, nowMs)
    private lateinit var store: LocationStore

    private fun TestScope.vm(compass: Compass): QiblaViewModel {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        store = LocationStore({ File(tmp.root, "location.json") }, backgroundScope)
        return QiblaViewModel({ places }, store, compass, dispatcher, now = { nowMs })
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun QiblaViewModel.await(condition: (QiblaUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(condition) } }

    private suspend fun saveCairo() =
        store.save(SavedLocation(LocationSource.CITY, cairo.cityId, cairo.latitude, cairo.longitude, "Africa/Cairo", 1))

    @Test
    fun withoutLocationAsksForOne() = runTest {
        val vm = vm(FakeCompass(available = true))
        assertEquals(QiblaUiState.NoLocation, vm.await { it != QiblaUiState.Loading })
    }

    @Test
    fun bearingIsTheGreatCircleQiblaEvenWithoutACompass() = runTest {
        val vm = vm(FakeCompass(available = false))
        saveCairo()
        val ready = vm.await { it is QiblaUiState.Ready } as QiblaUiState.Ready
        assertEquals(PrayerCalculator.qiblaBearing(GeoPoint(cairo.latitude, cairo.longitude)), ready.qiblaTrue, 1e-9)
        assertFalse(ready.hasCompass)
        vm.startCompass { 0 } // no-op without a compass
        assertEquals(null, (vm.uiState.value as QiblaUiState.Ready).headingTrue)
    }

    @Test
    fun headingAddsDeclinationAndDetectsAlignment() = runTest {
        val compass = FakeCompass(available = true)
        val vm = vm(compass)
        saveCairo()
        val ready = vm.await { it is QiblaUiState.Ready } as QiblaUiState.Ready
        vm.startCompass { 0 }
        // Magnetic azimuth such that true heading = qibla exactly.
        compass.readings.emit(CompassReading(ready.qiblaTrue - declination, CompassAccuracy.HIGH, 48.0))
        val aligned = vm.await { (it as? QiblaUiState.Ready)?.headingTrue != null } as QiblaUiState.Ready
        assertEquals(ready.qiblaTrue, aligned.headingTrue!!, 1e-6)
        assertTrue(aligned.aligned)
        assertFalse(aligned.needsCalibration || aligned.interference)
    }

    @Test
    fun lowAccuracyAndStrongFieldsAreFlagged() = runTest {
        val compass = FakeCompass(available = true)
        val vm = vm(compass)
        saveCairo()
        vm.await { it is QiblaUiState.Ready }
        vm.startCompass { 0 }
        compass.readings.emit(CompassReading(10.0, CompassAccuracy.UNRELIABLE, 150.0))
        val flagged = vm.await { (it as? QiblaUiState.Ready)?.headingTrue != null } as QiblaUiState.Ready
        assertTrue(flagged.needsCalibration)
        assertTrue(flagged.interference)
    }
}
