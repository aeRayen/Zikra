package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.zikra.app.adhan.AlertMode
import com.zikra.app.prayer.MonthDay
import com.zikra.app.prayer.PrayerMonthUiState
import com.zikra.app.prayer.PrayerSettingsUiState
import com.zikra.app.ui.HIJRI_PREVIEW_TAG
import com.zikra.app.ui.MONTH_NEXT_TAG
import com.zikra.app.ui.MONTH_PREVIOUS_TAG
import com.zikra.app.ui.MONTH_TITLE_TAG
import com.zikra.app.ui.PrayerMonthScreen
import com.zikra.app.ui.PrayerSettingsActions
import com.zikra.app.ui.PrayerSettingsScreen
import com.zikra.app.ui.adjustTag
import com.zikra.app.ui.adjustValueTag
import com.zikra.app.ui.alertTag
import com.zikra.app.ui.methodTag
import com.zikra.app.ui.monthRowTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.HijriCalendar
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.HighLatitudeRule
import com.zikra.core.prayer.PrayerName
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerSettingsAndMonthScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private val events = mutableListOf<String>()

    private val actions = object : PrayerSettingsActions {
        override val onMethod: (CalculationMethodId?) -> Unit = { events += "method:${it?.id}" }
        override val onMadhab: (AsrMadhab?) -> Unit = { events += "madhab:${it?.name}" }
        override val onHighLatitudeRule: (HighLatitudeRule?) -> Unit = { events += "rule:${it?.name}" }
        override val onAdjust: (PrayerName, Int) -> Unit = { prayer, delta -> events += "adjust:${prayer.name}:$delta" }
        override val onAlert: (
            PrayerName,
            AlertMode
        ) -> Unit = { prayer, mode -> events += "alert:${prayer.name}:${mode.name}" }
        override val onHijri: (Int) -> Unit = { events += "hijri:$it" }
        override val onReset: () -> Unit = { events += "reset" }
    }

    private fun settings(state: PrayerSettingsUiState) =
        rule.setContent { ZikraTheme { PrayerSettingsScreen(state, actions, onBack = { events += "back" }) } }

    @Test
    fun automaticOptionNamesTheMethodAndIsSelectedByDefault() {
        settings(PrayerSettingsUiState(loaded = true, automaticMethod = CalculationMethodId.UMM_AL_QURA))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(methodTag(null)))
        rule.onNodeWithTag(methodTag(null))
            .assertIsSelected()
            .assertTextContains(str(R.string.settings_automatic_format, str(R.string.method_umm_al_qura)))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(methodTag(CalculationMethodId.EGYPTIAN)))
        rule.onNodeWithTag(methodTag(CalculationMethodId.EGYPTIAN)).performClick()
        assertEquals(listOf("method:egyptian"), events)
    }

    @Test
    fun adjustmentButtonsAreSpokenAndReportTheirDirection() {
        settings(PrayerSettingsUiState(loaded = true, adjustments = mapOf(PrayerName.ISHA to 3)))
        val isha = str(R.string.prayer_isha)
        val plus = str(R.string.settings_adjust_plus, isha)
        rule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(plus))
        rule.onNodeWithContentDescription(plus).performClick()
        rule.onNodeWithContentDescription(str(R.string.settings_adjust_minus, isha)).performClick()
        rule.onNodeWithTag(adjustValueTag(PrayerName.ISHA)).assertTextEquals(Bidi.isolate("+3"))
        assertEquals(listOf("adjust:ISHA:1", "adjust:ISHA:-1"), events)
        rule.onNodeWithTag(adjustTag(PrayerName.FAJR, 1)).assertExists()
    }

    @Test
    fun alertModeIsChosenPerPrayer() {
        settings(PrayerSettingsUiState(loaded = true))
        rule.onNodeWithTag(alertTag(PrayerName.FAJR, AlertMode.SOUND)).assertIsSelected()
        rule.onNodeWithTag(alertTag(PrayerName.FAJR, AlertMode.SILENT)).performClick()
        assertEquals(listOf("alert:FAJR:SILENT"), events)
    }

    @Test
    fun hijriAdjustmentShowsTodayAndSteps() {
        val today = requireNotNull(HijriCalendar.fromGregorian(LocalDate.of(2026, 2, 18)))
        settings(PrayerSettingsUiState(loaded = true, hijriToday = today))
        rule.onNode(hasScrollAction()).performScrollToNode(hasTestTag(HIJRI_PREVIEW_TAG))
        rule.onNodeWithTag(HIJRI_PREVIEW_TAG).assertTextContains("1447", substring = true)
        rule.onNodeWithContentDescription(str(R.string.settings_hijri_plus)).performClick()
        rule.onNodeWithContentDescription(str(R.string.settings_hijri_minus)).performClick()
        assertEquals(listOf("hijri:1", "hijri:-1"), events)
    }

    @Test
    fun saveFailureIsShown() {
        settings(
            PrayerSettingsUiState(
                loaded = true,
                saveFailed = true,
                madhab = AsrMadhab.HANAFI,
                highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE
            )
        )
        rule.onNodeWithText(str(R.string.error_save_failed)).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithText(text: String) =
        onNode(androidx.compose.ui.test.hasText(text))

    // --- Monthly timetable ---

    private val june = YearMonth.of(2026, 6)
    private val days = (1..june.lengthOfMonth()).map { d ->
        MonthDay(june.atDay(d), PrayerName.entries.mapIndexed { i, _ -> LocalTime.of(4 + i * 3, 5) })
    }

    @Test
    fun monthShowsTitleRowsAndNavigates() {
        rule.setContent {
            ZikraTheme {
                PrayerMonthScreen(
                    PrayerMonthUiState.Ready(june, LocalDate.of(2026, 6, 15), days),
                    onBack = { events += "back" },
                    onPrevious = { events += "previous" },
                    onNext = { events += "next" },
                )
            }
        }
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextContains("2026", substring = true)
        rule.onNodeWithTag(monthRowTag(1)).assertTextContains("4:05", substring = true)
        rule.onNodeWithTag(MONTH_NEXT_TAG).performClick()
        rule.onNodeWithTag(MONTH_PREVIOUS_TAG).performClick()
        assertEquals(listOf("next", "previous"), events)
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicMonthUsesArabicDigits() {
        rule.setContent {
            ZikraTheme {
                PrayerMonthScreen(PrayerMonthUiState.Ready(june, LocalDate.of(2026, 6, 15), days), {}, {}, {})
            }
        }
        rule.onNodeWithTag(MONTH_TITLE_TAG).assertTextContains("٢٠٢٦", substring = true)
        rule.onNodeWithTag(monthRowTag(1)).assertTextContains("٤:٠٥", substring = true)
    }
}
