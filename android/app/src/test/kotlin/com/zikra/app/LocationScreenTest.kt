package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.zikra.app.location.CurrentLocation
import com.zikra.app.location.LocateStatus
import com.zikra.app.location.LocationUiState
import com.zikra.app.ui.CURRENT_LOCATION_TAG
import com.zikra.app.ui.LocationScreen
import com.zikra.app.ui.SEARCH_FIELD_TAG
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.Bidi
import com.zikra.core.places.Place
import com.zikra.core.settings.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private val city = Place(1, "Testville", "Region", "Country", "AA", 1.0, 2.0, ZoneId.of("UTC"))

    private fun show(
        state: LocationUiState,
        onQuery: (String) -> Unit = {},
        onPick: (Place) -> Unit = {},
        onDevice: () -> Unit = {},
    ) {
        rule.mainClock.autoAdvance = false // the «locating» spinner animates forever
        rule.setContent {
            ZikraTheme {
                LocationScreen(
                    state,
                    onBack = {},
                    onQueryChange = onQuery,
                    onPick = onPick,
                    onUseDeviceLocation = onDevice
                )
            }
        }
        rule.mainClock.advanceTimeBy(FRAME_MS)
    }

    @Test
    fun withoutLocationExplainsAndOffersBothWays() {
        var deviceTaps = 0
        show(LocationUiState(loaded = true), onDevice = { deviceTaps++ })
        rule.onNodeWithText(str(R.string.location_not_set)).assertIsDisplayed()
        rule.onNodeWithTag(SEARCH_FIELD_TAG).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.location_use_device)).performClick()
        assertEquals(1, deviceTaps)
    }

    @Test
    fun deniedPermissionShowsGuidanceAndSearchStillWorks() {
        val typed = mutableListOf<String>()
        show(LocationUiState(loaded = true, status = LocateStatus.PERMISSION_DENIED), onQuery = { typed += it })
        rule.onNodeWithText(str(R.string.location_denied)).assertIsDisplayed()
        rule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("tes")
        assertEquals(listOf("tes"), typed)
    }

    @Test
    fun resultsArePickable() {
        val picked = mutableListOf<Place>()
        show(LocationUiState(loaded = true, query = "tes", results = listOf(city)), onPick = { picked += it })
        rule.onNodeWithText("Testville").performClick()
        assertEquals(listOf(city), picked)
    }

    @Test
    fun emptyResultsSayWhatToDo() {
        show(LocationUiState(loaded = true, query = "zzz"))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(str(R.string.location_no_results)))
        rule.onNodeWithText(str(R.string.location_no_results)).assertIsDisplayed()
    }

    @Test
    fun deviceLocationIsLabelledAsApproximate() {
        show(LocationUiState(loaded = true, current = CurrentLocation(LocationSource.DEVICE, city, 1.0, 2.0)))
        val expected = str(R.string.location_near_city, Bidi.isolate("Testville"))
        assertEquals(expected, rule.onNodeWithTag(CURRENT_LOCATION_TAG).fetchText())
    }

    @Test
    fun buttonIsDisabledWhileLocating() {
        show(LocationUiState(loaded = true, status = LocateStatus.LOCATING))
        rule.onNodeWithText(str(R.string.location_locating)).assertIsDisplayed()
        rule.onNodeWithText(
            str(R.string.location_use_device)
        ).assertIsNotEnabled() // the label is merged into the button node
    }

    @Test
    fun geonamesAttributionIsShown() {
        show(LocationUiState(loaded = true))
        rule.onNode(hasScrollAction()).performScrollToNode(hasText(str(R.string.location_attribution)))
        rule.onNodeWithText(str(R.string.location_attribution)).assertIsDisplayed()
    }

    private fun SemanticsNodeInteraction.fetchText(): String =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private companion object {
        const val FRAME_MS = 500L
    }
}
