package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zikra.app.qibla.QiblaUiState
import com.zikra.app.ui.QIBLA_BEARING_TAG
import com.zikra.app.ui.QIBLA_INSTRUCTION_TAG
import com.zikra.app.ui.QiblaScreen
import com.zikra.app.ui.qiblaMessageTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.Bidi
import com.zikra.core.settings.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QiblaScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private fun ready(heading: Double? = null, aligned: Boolean = false, compass: Boolean = true) = QiblaUiState.Ready(
        place = null,
        source = LocationSource.CITY,
        qiblaTrue = 136.1,
        hasCompass = compass,
        headingTrue = heading,
        aligned = aligned,
    )

    private fun show(state: QiblaUiState, onChoose: () -> Unit = {}) =
        rule.setContent { ZikraTheme { QiblaScreen(state, onBack = {}, onChooseLocation = onChoose) } }

    @Test
    fun bearingIsAlwaysShownRounded() {
        show(ready())
        rule.onNodeWithTag(QIBLA_BEARING_TAG).assertTextEquals(str(R.string.qibla_bearing, Bidi.isolate("136")))
    }

    @Test
    fun tellsWhichWayToTurn() {
        show(ready(heading = 100.0)) // Qibla is 36° clockwise
        rule.onNodeWithTag(QIBLA_INSTRUCTION_TAG).assertTextEquals(str(R.string.qibla_turn_right, Bidi.isolate("36")))
    }

    @Test
    fun turnLeftWhenTheQiblaIsAnticlockwise() {
        show(ready(heading = 150.0))
        rule.onNodeWithTag(QIBLA_INSTRUCTION_TAG).assertTextEquals(str(R.string.qibla_turn_left, Bidi.isolate("14")))
    }

    @Test
    fun alignedIsAnnounced() {
        show(ready(heading = 136.0, aligned = true))
        rule.onNodeWithTag(QIBLA_INSTRUCTION_TAG).assertTextEquals(str(R.string.qibla_aligned))
    }

    @Test
    fun phoneWithoutCompassGetsTheAngleAndAnExplanation() {
        show(ready(compass = false))
        rule.onNodeWithTag(qiblaMessageTag(R.string.qibla_no_compass)).assertExists()
        rule.onNodeWithTag(QIBLA_INSTRUCTION_TAG).assertDoesNotExist()
    }

    @Test
    fun warningsAppearWhenTheCompassCannotBeTrusted() {
        show(ready(heading = 10.0).copy(needsCalibration = true, interference = true))
        rule.onNodeWithTag(qiblaMessageTag(R.string.qibla_calibrate)).assertExists()
        rule.onNodeWithTag(qiblaMessageTag(R.string.qibla_interference)).assertExists()
    }

    @Test
    fun withoutLocationLeadsToThePicker() {
        var taps = 0
        show(QiblaUiState.NoLocation, onChoose = { taps++ })
        rule.onNodeWithText(str(R.string.prayer_choose_location)).assertIsDisplayed().performClick()
        assertEquals(1, taps)
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicUsesArabicDigits() {
        show(ready(heading = 100.0))
        rule.onNodeWithTag(QIBLA_BEARING_TAG).assertTextContains("١٣٦", substring = true)
        rule.onNodeWithTag(QIBLA_INSTRUCTION_TAG).assertTextContains("٣٦", substring = true)
    }
}
