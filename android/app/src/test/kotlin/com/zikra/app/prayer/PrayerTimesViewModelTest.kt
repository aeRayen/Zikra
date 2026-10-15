package com.zikra.app.prayer

import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerTimesViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val makkahCity = Place(104515, "Makkah", null, "Saudi Arabia", "SA", 21.42664, 39.82563, riyadh)

    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = makkahCity.takeIf { cityId == it.cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }

    private var now: Instant = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, riyadh).toInstant()
    private var phoneZone: ZoneId = riyadh
    private val ticker = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

    private class Fixture(val vm: PrayerTimesViewModel, val location: LocationStore, val prefs: PrayerPreferencesStore)

    private fun TestScope.fixture(): Fixture {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val location = LocationStore({ File(tmp.root, LocationStore.FILE_NAME) }, backgroundScope)
        val prefs = PrayerPreferencesStore({ File(tmp.root, PrayerPreferencesStore.FILE_NAME) }, backgroundScope)
        val vm = PrayerTimesViewModel(
            places = { places },
            locationStore = location,
            preferencesStore = prefs,
            io = dispatcher,
            clock = { Clock.fixed(now, phoneZone) },
            ticker = ticker,
        )
        return Fixture(vm, location, prefs)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** DataStore reads on a real thread: wait for the state, not for the test scheduler. */
    private suspend fun PrayerTimesViewModel.await(condition: (PrayerTimesUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(condition) } }

    private suspend fun PrayerTimesViewModel.ready() =
        await { it is PrayerTimesUiState.Ready } as PrayerTimesUiState.Ready

    private suspend fun LocationStore.saveMakkah(cityId: Long? = makkahCity.cityId) =
        save(SavedLocation(LocationSource.CITY, cityId, makkahCity.latitude, makkahCity.longitude, "Asia/Riyadh", 1))

    private val makkahPoint = GeoPoint(makkahCity.latitude, makkahCity.longitude)

    @Test
    fun withoutLocationAsksForOne() = runTest {
        val f = fixture()
        assertEquals(PrayerTimesUiState.NoLocation, f.vm.await { it != PrayerTimesUiState.Loading })
    }

    @Test
    fun makkahUsesUmmAlQuraAutomaticallyAndMatchesTheEngine() = runTest {
        val f = fixture()
        f.location.saveMakkah()
        val state = f.vm.ready()
        assertEquals(CalculationMethodId.UMM_AL_QURA, state.method)
        assertTrue(state.methodIsAutomatic)
        val expected = PrayerCalculator.next(makkahPoint, now, riyadh, PrayerSettings(CalculationMethodId.UMM_AL_QURA))
        assertEquals(expected.prayer, state.next)
        assertEquals(PrayerName.DHUHR, state.next) // noon in Makkah, before Dhuhr
        assertFalse(state.nextIsTomorrow)
        val minutes = Duration.between(now, expected.at).toSeconds().let { (it + 59) / 60 }
        assertEquals(minutes, state.minutesUntilNext)
        assertEquals(PrayerName.entries, state.times.map { it.first })
        assertFalse(state.zoneDiffersFromDevice)
    }

    @Test
    fun afterIshaTheNextPrayerIsTomorrowsFajr() = runTest {
        now = ZonedDateTime.of(2026, 6, 1, 23, 30, 0, 0, riyadh).toInstant()
        val f = fixture()
        f.location.saveMakkah()
        val state = f.vm.ready()
        assertEquals(PrayerName.FAJR, state.next)
        assertTrue(state.nextIsTomorrow)
    }

    @Test
    fun userMethodOverridesTheRegionalDefault() = runTest {
        val f = fixture()
        f.location.saveMakkah()
        f.prefs.update { it.copy(methodId = CalculationMethodId.EGYPTIAN.id) }
        val state = f.vm.await { it is PrayerTimesUiState.Ready && it.method == CalculationMethodId.EGYPTIAN }
        assertFalse((state as PrayerTimesUiState.Ready).methodIsAutomatic)
    }

    @Test
    fun phoneInAnotherZoneIsFlagged() = runTest {
        phoneZone = ZoneId.of("Europe/Berlin")
        val f = fixture()
        f.location.saveMakkah()
        assertTrue(f.vm.ready().zoneDiffersFromDevice)
    }

    @Test
    fun countdownAdvancesOnEveryTick() = runTest {
        val f = fixture()
        f.location.saveMakkah()
        val first = f.vm.ready().minutesUntilNext
        now = now.plusSeconds(5 * 60)
        ticker.emit(Unit)
        f.vm.await { it is PrayerTimesUiState.Ready && it.minutesUntilNext == first - 5 }
    }

    @Test
    fun unknownCityStillShowsTimesWithTheGlobalDefault() = runTest {
        val f = fixture()
        f.location.saveMakkah(cityId = 999)
        val state = f.vm.ready()
        assertNull(state.place)
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, state.method)
    }
}
