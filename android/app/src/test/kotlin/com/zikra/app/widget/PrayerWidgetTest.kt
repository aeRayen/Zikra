package com.zikra.app.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.R
import com.zikra.app.prayer.PrayerTimesSource
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationSource
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.core.settings.SavedLocation
import com.zikra.platform.alarm.AlarmKind
import com.zikra.platform.alarm.AlarmScheduler
import com.zikra.platform.alarm.ReplanReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
// A bare Application: ZikraApplication would start its own observers that update the same widget from
// another thread at the same time (a race that made this test flaky on CI).
@Config(sdk = [34], application = Application::class)
class PrayerWidgetTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Asia/Riyadh")
    private val makkah = Place(104515, "Makkah", null, "Saudi Arabia", "SA", 21.42664, 39.82563, zone)
    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = makkah.takeIf { it.cityId == cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val location = LocationStore({ File(tmp.root, "location.json") }, scope)
    private val prefs = PrayerPreferencesStore({ File(tmp.root, "prayer.json") }, scope)

    /** Future noon: the real scheduler ignores past times. Dhuhr in Makkah is always after noon. */
    private var now: Instant = LocalDate.now(zone).plusDays(5).atTime(12, 0).atZone(zone).toInstant()
    private val widget = PrayerWidget(
        PrayerTimesSource(
            { places },
            location,
            prefs,
            Dispatchers.Unconfined,
            clock = { Clock.fixed(now, zone) },
            ticker = flowOf(Unit)
        ),
    )

    @After
    fun tearDown() {
        AlarmScheduler.get(context).cancelAll(AlarmKind.WIDGET)
        scope.cancel()
    }

    private fun addWidget(): Int =
        shadowOf(
            AppWidgetManager.getInstance(context)
        ).createWidget(PrayerWidgetProvider::class.java, R.layout.widget_next_prayer)

    private fun text(id: Int, view: Int) =
        shadowOf(AppWidgetManager.getInstance(context)).getViewFor(id).findViewById<TextView>(view).text.toString()

    private suspend fun saveMakkah() =
        location.save(SavedLocation(LocationSource.CITY, makkah.cityId, makkah.latitude, makkah.longitude, zone.id, 1))

    @Test
    fun showsTheNextPrayerAndSchedulesTheNextRefresh() = runBlocking {
        val id = addWidget()
        saveMakkah()
        widget.refresh(context)
        assertTrue(text(id, R.id.widget_title).contains(context.getString(R.string.prayer_dhuhr)))
        assertEquals("Makkah", text(id, R.id.widget_place))
        val refresh = AlarmScheduler.get(context).scheduled().single { it.kind == AlarmKind.WIDGET }
        assertTrue(refresh.triggerAtEpochMs > now.toEpochMilli())
    }

    @Test
    fun aTimeOrZoneChangeRefreshesTheWidget() = runBlocking {
        val id = addWidget()
        saveMakkah()
        widget.refresh(context)
        val before = text(id, R.id.widget_title)
        now = now.plusSeconds(6 * 3600) // evening: the next prayer is no longer Dhuhr
        widget.replan(context, ReplanReason.TIME_ZONE_CHANGED)
        assertNotEquals(before, text(id, R.id.widget_title))
    }

    @Test
    fun withoutLocationTheWidgetAsksToOpenTheApp() = runBlocking {
        val id = addWidget()
        widget.refresh(context)
        assertEquals(context.getString(R.string.widget_no_location), text(id, R.id.widget_title))
    }

    @Test
    fun noWidgetMeansNoRefreshAlarm() = runBlocking {
        saveMakkah()
        widget.refresh(context)
        assertTrue(AlarmScheduler.get(context).scheduled().none { it.kind == AlarmKind.WIDGET })
    }

    private val ready = PrayerTimesUiState.Ready(
        place = makkah,
        source = LocationSource.CITY,
        zone = zone,
        zoneDiffersFromDevice = false,
        date = LocalDate.of(2026, 6, 1),
        times = PrayerName.entries.map { it to LocalTime.NOON },
        next = PrayerName.FAJR,
        nextTime = LocalTime.of(4, 11),
        nextIsTomorrow = true,
        minutesUntilNext = 300,
        method = CalculationMethodId.UMM_AL_QURA,
        methodIsAutomatic = true,
        madhab = AsrMadhab.STANDARD,
    )

    @Test
    fun textSaysTomorrowForFajrAfterIsha() {
        val text = requireNotNull(widgetText(context, ready))
        assertTrue(text.title.contains("tomorrow"))
        assertNull(widgetText(context, PrayerTimesUiState.Loading))
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicWidgetUsesArabicDigits() {
        val text = requireNotNull(widgetText(context, ready))
        assertTrue(text.title, text.title.contains("٤:١١"))
    }
}
