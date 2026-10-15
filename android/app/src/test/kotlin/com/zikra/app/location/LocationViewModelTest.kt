package com.zikra.app.location

import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.SavedLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Names carry the language so tests can see which one was used. */
    private class FakePlaces : PlaceLookup {
        var nearestAnswer: Pair<Long, Double>? = null
        val searches = mutableListOf<String>()

        fun place(id: Long, lang: String) = Place(
            cityId = id,
            name = "city$id-$lang",
            region = null,
            country = "country-$lang",
            countryCode = "SA",
            latitude = 21.4,
            longitude = 39.8,
            timeZone = ZoneId.of("Asia/Riyadh"),
        )

        override fun search(query: String, language: String, limit: Int): List<Place> {
            searches += query
            return if (query.startsWith("mak")) listOf(place(1, language), place(2, language)) else emptyList()
        }

        override fun byId(cityId: Long, language: String) = place(cityId, language)

        override fun nearest(latitude: Double, longitude: Double, language: String) =
            nearestAnswer?.let { (id, km) -> NearestPlace(place(id, language), km) }
    }

    private val places = FakePlaces()
    private var locate: LocateResult = LocateResult.Unavailable

    private fun TestScope.viewModel(): Pair<LocationViewModel, LocationStore> {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val store = LocationStore({ File(tmp.root, LocationStore.FILE_NAME) }, backgroundScope)
        val vm = LocationViewModel(
            places = { places },
            store = store,
            locator = { locate },
            io = dispatcher,
            systemZone = { "Europe/Berlin" },
            clock = { 42L },
        )
        return vm to store
    }

    /** DataStore reads its file on a real thread, so wait for the state instead of the test scheduler. */
    private suspend fun LocationViewModel.awaitState(condition: (LocationUiState) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(5_000) { uiState.first(condition) } }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun startsLoadedWithoutLocation() = runTest {
        val (vm, _) = viewModel()
        val state = vm.awaitState { it.loaded }
        assertNull(state.current)
    }

    @Test
    fun typingSearchesOnceAfterTheDebounce() = runTest {
        val (vm, _) = viewModel()
        vm.onQueryChange("m")
        vm.onQueryChange("ma")
        vm.onQueryChange("mak")
        advanceUntilIdle()
        assertEquals(listOf("mak"), places.searches)
        assertEquals(listOf(1L, 2L), vm.uiState.value.results.map { it.cityId })
        vm.onQueryChange("")
        assertTrue(vm.uiState.value.results.isEmpty())
    }

    @Test
    fun pickingACitySavesItWithItsZoneAndShowsIt() = runTest {
        val (vm, store) = viewModel()
        vm.onQueryChange("mak")
        advanceUntilIdle()
        vm.onPick(vm.uiState.value.results.first())
        advanceUntilIdle()
        assertEquals(SavedLocation(LocationSource.CITY, 1, 21.4, 39.8, "Asia/Riyadh", 42), store.location.first())
        val state = vm.awaitState { it.current != null }
        assertEquals("", state.query)
        assertEquals(1L, state.current?.place?.cityId)
        assertEquals(LocationSource.CITY, state.current?.source)
    }

    @Test
    fun refusingThePermissionStillLetsTheUserSearchAndPick() = runTest {
        val (vm, store) = viewModel()
        vm.onLocationPermissionResult(granted = false)
        assertEquals(LocateStatus.PERMISSION_DENIED, vm.uiState.value.status)
        vm.onQueryChange("mak")
        advanceUntilIdle()
        vm.onPick(vm.uiState.value.results.first())
        advanceUntilIdle()
        assertEquals(1L, store.location.first()?.cityId)
        assertEquals(LocateStatus.IDLE, vm.uiState.value.status)
    }

    @Test
    fun deviceLocationSavesTheFixWithTheNearestCityAndItsZone() = runTest {
        val (vm, store) = viewModel()
        locate = LocateResult.Found(21.46, 39.85)
        places.nearestAnswer = 7L to 4.0
        vm.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(SavedLocation(LocationSource.DEVICE, 7, 21.46, 39.85, "Asia/Riyadh", 42), store.location.first())
        assertEquals(LocateStatus.IDLE, vm.uiState.value.status)
        assertEquals(LocationSource.DEVICE, vm.awaitState { it.current != null }.current?.source)
    }

    @Test
    fun deviceLocationFarFromAnyCityUsesThePhoneZone() = runTest {
        val (vm, store) = viewModel()
        locate = LocateResult.Found(-60.0, -140.0)
        vm.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(
            SavedLocation(LocationSource.DEVICE, null, -60.0, -140.0, "Europe/Berlin", 42),
            store.location.first()
        )
        assertNull(vm.awaitState { it.current != null }.current?.place)
    }

    @Test
    fun locationOffOrRevokedIsReportedAndNothingIsSaved() = runTest {
        val (vm, store) = viewModel()
        locate = LocateResult.Unavailable
        vm.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(LocateStatus.UNAVAILABLE, vm.uiState.value.status)
        locate = LocateResult.NoPermission
        vm.onLocationPermissionResult(granted = true)
        advanceUntilIdle()
        assertEquals(LocateStatus.PERMISSION_DENIED, vm.uiState.value.status)
        assertNull(store.location.first())
    }

    @Test
    fun aFailedWriteIsReportedInsteadOfCrashing() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        // The parent "directory" is a regular file, so every write fails like a broken or full disk.
        val blocker = tmp.newFile("not-a-directory")
        val broken = LocationStore({ File(blocker, LocationStore.FILE_NAME) }, backgroundScope)
        val vm = LocationViewModel({ places }, broken, { locate }, UnconfinedTestDispatcher(testScheduler), { "UTC" })
        vm.onPick(places.place(1, "en"))
        vm.awaitState { it.status == LocateStatus.SAVE_FAILED }
    }

    @Test
    fun changingTheLanguageRenamesTheSavedCity() = runTest {
        val (vm, store) = viewModel()
        store.save(SavedLocation(LocationSource.CITY, 3, 1.0, 2.0, "UTC", 1))
        vm.setLanguage("ar")
        vm.awaitState { it.current?.place?.name == "city3-ar" }
        vm.setLanguage("en")
        vm.awaitState { it.current?.place?.name == "city3-en" }
    }
}
