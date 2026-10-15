package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.app.ui.ALERTS_NOTICE_TAG
import com.zikra.app.ui.AlertsNotice
import com.zikra.app.ui.CHOOSE_LOCATION_TAG
import com.zikra.app.ui.NEXT_PRAYER_TAG
import com.zikra.app.ui.PRAYER_DATE_TAG
import com.zikra.app.ui.PRAYER_METHOD_TAG
import com.zikra.app.ui.PrayerTimesScreen
import com.zikra.app.ui.prayerRowTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.HijriCalendar
import com.zikra.core.places.Place
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerTimesScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int, vararg args: Any) = rule.activity.getString(id, *args)

    private val zone = ZoneId.of("Asia/Riyadh")
    private val city = Place(1, "Testville", null, "Country", "SA", 21.4, 39.8, zone)

    private fun ready(
        minutes: Long = 80,
        next: PrayerName = PrayerName.DHUHR,
        tomorrow: Boolean = false,
        automatic: Boolean = true,
        zoneDiffers: Boolean = false,
    ) = PrayerTimesUiState.Ready(
        place = city,
        source = LocationSource.CITY,
        zone = zone,
        zoneDiffersFromDevice = zoneDiffers,
        date = LocalDate.of(2026, 6, 1),
        times = listOf(
            PrayerName.FAJR to LocalTime.of(4, 11),
            PrayerName.SUNRISE to LocalTime.of(5, 38),
            PrayerName.DHUHR to LocalTime.of(12, 18),
            PrayerName.ASR to LocalTime.of(15, 38),
            PrayerName.MAGHRIB to LocalTime.of(18, 59),
            PrayerName.ISHA to LocalTime.of(20, 29),
        ),
        next = next,
        nextTime = LocalTime.of(12, 18),
        nextIsTomorrow = tomorrow,
        minutesUntilNext = minutes,
        method = CalculationMethodId.UMM_AL_QURA,
        methodIsAutomatic = automatic,
        madhab = AsrMadhab.STANDARD,
    )

    private fun show(
        state: PrayerTimesUiState,
        onChoose: () -> Unit = {},
        notice: AlertsNotice = AlertsNotice.NONE,
        onFix: () -> Unit = {},
    ) {
        rule.setContent {
            ZikraTheme {
                PrayerTimesScreen(
                    state = state,
                    onChooseLocation = onChoose,
                    alertsNotice = notice,
                    onFixAlerts = onFix
                )
            }
        }
    }

    @Test
    fun alertsNoticeExplainsAndOffersTheFix() {
        var fixes = 0
        show(ready(), notice = AlertsNotice.NEED_NOTIFICATION_PERMISSION, onFix = { fixes++ })
        rule.onNodeWithText(str(R.string.alerts_permission_message)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.alerts_permission_button)).performClick()
        assertEquals(1, fixes)
    }

    @Test
    fun inexactDeliveryIsExplained() {
        show(ready(), notice = AlertsNotice.INEXACT_DELIVERY)
        rule.onNodeWithText(str(R.string.alerts_inexact_title)).assertIsDisplayed()
    }

    @Test
    fun noNoticeWhenAlertsWork() {
        show(ready())
        rule.onNodeWithTag(ALERTS_NOTICE_TAG).assertDoesNotExist()
    }

    @Test
    fun withoutLocationOffersToChooseOne() {
        var taps = 0
        show(PrayerTimesUiState.NoLocation, onChoose = { taps++ })
        rule.onNodeWithText(str(R.string.prayer_no_location_title)).assertIsDisplayed()
        rule.onNodeWithTag(CHOOSE_LOCATION_TAG).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun showsAllSixTimesTheNextPrayerAndTheMethod() {
        show(ready())
        PrayerName.entries.forEach { rule.onNodeWithTag(prayerRowTag(it)).assertExists() }
        rule.onNodeWithTag(prayerRowTag(PrayerName.DHUHR)).assertTextContains(str(R.string.prayer_dhuhr))
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("1 hour and 20 minutes", substring = true)
        val method = str(R.string.prayer_method_used, str(R.string.method_umm_al_qura))
        rule.onNodeWithTag(PRAYER_METHOD_TAG).assertTextContains(method)
        rule.onNodeWithText(str(R.string.prayer_method_automatic)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun dateLineShowsGregorianAndHijri() {
        val hijri = HijriCalendar.fromGregorian(LocalDate.of(2026, 6, 1))
        show(ready().copy(hijri = hijri))
        rule.onNodeWithTag(PRAYER_DATE_TAG).assertTextContains("2026", substring = true)
        rule.onNodeWithTag(PRAYER_DATE_TAG).assertTextContains("1447", substring = true)
    }

    @Test
    fun countdownDropsZeroParts() {
        show(ready(minutes = 120))
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("in 2 hours", substring = true)
    }

    @Test
    fun tomorrowsFajrIsLabelled() {
        show(ready(minutes = 5, next = PrayerName.FAJR, tomorrow = true))
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("tomorrow", substring = true)
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("5 minutes", substring = true)
    }

    @Test
    fun differentZoneIsExplained() {
        show(ready(zoneDiffers = true))
        rule.onNodeWithText(
            str(R.string.prayer_zone_note, "").substringBefore("(").trim(),
            substring = true
        ).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicUsesArabicDigitsAndPlurals() {
        show(ready(minutes = 61))
        // 61 minutes = «ساعة واحدة ودقيقة واحدة»: Arabic singular forms, no digits needed.
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("ساعة واحدة ودقيقة واحدة", substring = true)
        rule.onNodeWithTag(prayerRowTag(PrayerName.ASR)).assertTextContains("٣:٣٨", substring = true)
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicFewPluralWithArabicDigits() {
        show(ready(minutes = 3 * 60 + 5))
        rule.onNodeWithTag(NEXT_PRAYER_TAG).assertTextContains("٣ ساعات و٥ دقائق", substring = true)
    }
}
