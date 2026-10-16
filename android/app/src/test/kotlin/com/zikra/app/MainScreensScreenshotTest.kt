package com.zikra.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeUiState
import com.zikra.app.location.CurrentLocation
import com.zikra.app.location.LocationUiState
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.app.quran.ReaderUiState
import com.zikra.app.quran.SavedAyah
import com.zikra.app.ui.HomeScreen
import com.zikra.app.ui.LocationScreen
import com.zikra.app.ui.PrayerTimesScreen
import com.zikra.app.ui.QuranReaderScreen
import com.zikra.app.ui.ReaderActions
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.HijriCalendar
import com.zikra.core.places.Place
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName
import com.zikra.core.quran.QuranText
import com.zikra.core.quran.QuranTranslation
import com.zikra.core.settings.LocationSource
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Golden screenshots of the main Phase 2 screens in Arabic and English (WP 2.3 / 2.10 acceptance).
 * Recorded on Linux CI (CONTRIBUTING §6); fixed data only, so the images never depend on the clock.
 * A bare Application keeps ZikraApplication's background observers out of the picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class MainScreensScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val quran = QuranText.open(context)
    private val english = QuranTranslation.openEnglish(context)

    @After
    fun tearDown() {
        quran.close()
        english.close()
    }

    private val zone = ZoneId.of("Asia/Riyadh")
    private val makkah = Place(104515, "Makkah", null, "Saudi Arabia", "SA", 21.42664, 39.82563, zone)
    private val date = LocalDate.of(2026, 3, 10)

    private val prayer = PrayerTimesUiState.Ready(
        place = makkah,
        source = LocationSource.CITY,
        zone = zone,
        zoneDiffersFromDevice = false,
        date = date,
        hijri = HijriCalendar.fromGregorian(date),
        times = listOf(
            PrayerName.FAJR to LocalTime.of(5, 7),
            PrayerName.SUNRISE to LocalTime.of(6, 22),
            PrayerName.DHUHR to LocalTime.of(12, 25),
            PrayerName.ASR to LocalTime.of(15, 48),
            PrayerName.MAGHRIB to LocalTime.of(18, 28),
            PrayerName.ISHA to LocalTime.of(19, 58),
        ),
        next = PrayerName.ASR,
        nextTime = LocalTime.of(15, 48),
        nextIsTomorrow = false,
        minutesUntilNext = 80,
        method = CalculationMethodId.UMM_AL_QURA,
        methodIsAutomatic = true,
        madhab = AsrMadhab.STANDARD,
    )

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false // no infinite animation may block the capture
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(FRAME_MS)
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun prayer(lang: String) = shot("prayer_$lang") {
        ZikraTheme { PrayerTimesScreen(prayer, onChooseLocation = {}) }
    }

    private fun home(lang: String) = shot("home_$lang") {
        val continueAt = SavedAyah(requireNotNull(quran.surah(18)), quran.ayahs(18)[9])
        ZikraTheme {
            HomeScreen(
                HomeUiState(true, listOf(HomeCard.NextPrayer(prayer), HomeCard.ContinueReading(continueAt)))
            )
        }
    }

    private fun location(lang: String) = shot("location_$lang") {
        val state = LocationUiState(
            current = CurrentLocation(LocationSource.CITY, makkah, makkah.latitude, makkah.longitude),
            loaded = true,
            query = "Mak",
            results = listOf(makkah),
        )
        ZikraTheme { LocationScreen(state, onBack = {}, onQueryChange = {}, onPick = {}, onUseDeviceLocation = {}) }
    }

    private fun reader(lang: String, translation: Boolean) = shot("reader_$lang") {
        val state = ReaderUiState.Ready(
            requireNotNull(quran.surah(1)),
            quran.ayahs(1),
            0,
            english.surah(1),
            english.info
        )
        QuranReaderScreen(
            state,
            textScale = 1f,
            night = false,
            actions = NoActions,
            bookmarks = setOf(2),
            showTranslation = translation
        )
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic() {
        prayer("ar")
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicHome() = home("ar")

    @Test
    @Config(qualifiers = "ar")
    fun arabicLocation() = location("ar")

    @Test
    @Config(qualifiers = "ar")
    fun arabicReader() = reader("ar", translation = false)

    @Test
    fun english() = prayer("en")

    @Test
    fun englishHome() = home("en")

    @Test
    fun englishLocation() = location("en")

    @Test
    fun englishReaderWithTranslation() = reader("en", translation = true)

    private object NoActions : ReaderActions {
        override val onBack = {}
        override val onSmaller = {}
        override val onBigger = {}
        override val onToggleNight: (Boolean) -> Unit = {}
        override val onAbout = {}
        override val onToggleBookmark: (Int) -> Unit = {}
        override val onRead: (Int) -> Unit = {}
        override val onToggleTranslation: (Boolean) -> Unit = {}
    }

    private companion object {
        const val FRAME_MS = 500L
    }
}
