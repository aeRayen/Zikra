package com.zikra.app.prayer

import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferences
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerSettingsAndMonthViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val london = ZoneId.of("Europe/London")
    private val londonCity = Place(2643743, "London", null, "United Kingdom", "GB", 51.50853, -0.12574, london)
    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = londonCity.takeIf { cityId == it.cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }

    private lateinit var location: LocationStore
    private lateinit var prefs: PrayerPreferencesStore

    private fun TestScope.stores() {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        location = LocationStore({ File(tmp.root, LocationStore.FILE_NAME) }, backgroundScope)
        prefs = PrayerPreferencesStore({ File(tmp.root, PrayerPreferencesStore.FILE_NAME) }, backgroundScope)
    }

    private suspend fun saveLondon() = location.save(
        SavedLocation(LocationSource.CITY, londonCity.cityId, londonCity.latitude, londonCity.longitude, london.id, 1),
    )

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** DataStore works on a real thread: wait for the value rather than for the test scheduler. */
    private suspend fun <T> Flow<T>.await(condition: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { first(condition) } }

    /**
     * Windows cannot replace a file another reader has open, so DataStore's write-then-rename fails
     * when the screen's observer and a write overlap. Android (Linux) has no such limit, and CI runs these
     * tests on Linux; on a Windows dev machine they are skipped instead of failing for that reason.
     */
    private fun assumeRenameOverOpenFileWorks() =
        assumeFalse(System.getProperty("os.name").orEmpty().startsWith("Windows"))

    // --- Settings ---

    @Test
    fun automaticOptionNamesTheRegionalDefault() = runTest {
        stores()
        saveLondon()
        val vm = PrayerSettingsViewModel({ places }, location, prefs, UnconfinedTestDispatcher(testScheduler))
        val state = vm.uiState.await { it.loaded && it.automaticMethod == CalculationMethodId.MOONSIGHTING_COMMITTEE }
        assertEquals(null, state.method)
        assertEquals(AsrMadhab.STANDARD, state.automaticMadhab)
    }

    @Test
    fun choicesArePersistedAndResetClearsThem() = runTest {
        assumeRenameOverOpenFileWorks()
        stores()
        val vm = PrayerSettingsViewModel({ places }, location, prefs, UnconfinedTestDispatcher(testScheduler))
        vm.setMethod(CalculationMethodId.EGYPTIAN)
        prefs.preferences.await { it.methodId == "egyptian" }
        vm.setMadhab(AsrMadhab.HANAFI)
        prefs.preferences.await { it.madhab == "HANAFI" }
        vm.setMethod(null)
        prefs.preferences.await { it.methodId == null }
        vm.reset()
        assertEquals(PrayerPreferences(), prefs.preferences.await { it.madhab == null })
    }

    @Test
    fun adjustmentsStayWithinThirtyMinutesAndZeroIsRemoved() = runTest {
        assumeRenameOverOpenFileWorks()
        stores()
        val vm = PrayerSettingsViewModel({ places }, location, prefs, UnconfinedTestDispatcher(testScheduler))
        // One step at a time, each awaited: like taps, and safe on Windows where a busy file cannot be replaced.
        for (step in 1..PrayerSettings.MAX_ADJUSTMENT + 5) {
            vm.adjust(PrayerName.FAJR, 1)
            val expected = minOf(step, PrayerSettings.MAX_ADJUSTMENT)
            prefs.preferences.await { it.adjustmentMinutes["FAJR"] == expected }
        }
        vm.adjust(PrayerName.ISHA, -1)
        prefs.preferences.await { it.adjustmentMinutes["ISHA"] == -1 }
        vm.adjust(PrayerName.ISHA, 1)
        val saved = prefs.preferences.await { "ISHA" !in it.adjustmentMinutes }
        assertEquals(mapOf("FAJR" to PrayerSettings.MAX_ADJUSTMENT), saved.adjustmentMinutes)
        vm.uiState.await { it.adjustments[PrayerName.FAJR] == PrayerSettings.MAX_ADJUSTMENT }
    }

    // --- Monthly timetable ---

    private fun TestScope.month(now: ZonedDateTime) = PrayerMonthViewModel(
        places = { places },
        locationStore = location,
        preferencesStore = prefs,
        io = UnconfinedTestDispatcher(testScheduler),
        clock = { Clock.fixed(now.toInstant(), now.zone) },
    )

    @Test
    fun currentMonthHasEveryDayMatchingTheEngine() = runTest {
        stores()
        saveLondon()
        val vm = month(ZonedDateTime.of(2026, 3, 10, 12, 0, 0, 0, london))
        val state = vm.uiState.await { it is PrayerMonthUiState.Ready } as PrayerMonthUiState.Ready
        assertEquals(YearMonth.of(2026, 3), state.month)
        assertEquals(31, state.days.size)
        assertEquals(LocalDate.of(2026, 3, 10), state.today)
        // 29 March 2026 is the UK clock change: local times on that row must come from the engine as is.
        val settings = PrayerSettings(CalculationMethodId.MOONSIGHTING_COMMITTEE)
        val engine = PrayerCalculator.compute(
            GeoPoint(londonCity.latitude, londonCity.longitude),
            LocalDate.of(2026, 3, 29),
            london,
            settings
        )
        val row = state.days.single { it.date.dayOfMonth == 29 }
        assertEquals(PrayerName.entries.map { engine[it].atZone(london).toLocalTime() }, row.times)
    }

    @Test
    fun navigatesBetweenMonthsAcrossTheYear() = runTest {
        stores()
        saveLondon()
        val vm = month(ZonedDateTime.of(2026, 12, 31, 23, 0, 0, 0, london))
        vm.uiState.await { it is PrayerMonthUiState.Ready }
        vm.nextMonth()
        val jan = vm.uiState.await { (it as? PrayerMonthUiState.Ready)?.month == YearMonth.of(2027, 1) }
        assertEquals(31, (jan as PrayerMonthUiState.Ready).days.size)
        vm.previousMonth()
        vm.previousMonth()
        val nov = vm.uiState.await { (it as? PrayerMonthUiState.Ready)?.month == YearMonth.of(2026, 11) }
        assertEquals(30, (nov as PrayerMonthUiState.Ready).days.size)
    }

    @Test
    fun monthWithoutLocationSaysSo() = runTest {
        stores()
        val vm = month(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, london))
        assertEquals(PrayerMonthUiState.NoLocation, vm.uiState.await { it != PrayerMonthUiState.Loading })
    }
}
