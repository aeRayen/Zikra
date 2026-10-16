package com.zikra.app.adhkar

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.ui.AdhkarRemindersActions
import com.zikra.app.ui.AdhkarRemindersScreen
import com.zikra.app.ui.REMINDER_MORNING_TAG
import com.zikra.app.ui.REMINDER_NEEDS_LOCATION_TAG
import com.zikra.app.ui.REMINDER_SLEEP_TAG
import com.zikra.core.adhkar.AdhkarBook
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import com.zikra.core.settings.AdhkarReminders
import com.zikra.core.settings.AdhkarRemindersStore
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
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
import java.time.ZoneId

/** WP 2.14c: adhkar reminders after Fajr and Asr and at bedtime. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AdhkarRemindersTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext<Application>()
    private val zone = ZoneId.of("Asia/Riyadh")

    // In the future: the real scheduler ignores past times by the real clock. Midnight, before Fajr.
    private val day: LocalDate = LocalDate.now(zone).plusDays(10)
    private val midnight: Instant = day.atStartOfDay(zone).toInstant()
    private val makkah = Place(104515, "Makkah", null, "Saudi Arabia", "SA", 21.42664, 39.82563, zone)
    private val point = GeoPoint(makkah.latitude, makkah.longitude)
    private val settings = PrayerSettings(CalculationMethodId.UMM_AL_QURA)
    private val place = PrayerPlace(point, zone, settings)

    private fun times(date: LocalDate) = PrayerCalculator.compute(point, date, zone, settings)

    @Test
    fun morningAndEveningFollowFajrAndAsrForThreeDays() {
        val plan = AdhkarReminderPlan.reminders(AdhkarReminders(morning = true, evening = true), place, zone, midnight)
        val parsed = plan.keys.mapNotNull(AdhkarReminderPlan::parse)
        assertEquals(6, parsed.size)
        val morning = parsed.filter { it.kind == AdhkarReminderKind.MORNING }.map { it.at }.sorted()
        val after = AdhkarReminderPlan.AFTER_PRAYER
        assertEquals((0L..2L).map { times(day.plusDays(it))[PrayerName.FAJR] + after }, morning)
        val evening = parsed.filter { it.kind == AdhkarReminderKind.EVENING }.map { it.at }.sorted()
        assertEquals((0L..2L).map { times(day.plusDays(it))[PrayerName.ASR] + after }, evening)
        assertTrue(parsed.all { it.kind.chapter == AdhkarBook.MORNING_EVENING })
    }

    @Test
    fun withoutALocationOnlyTheSleepReminderIsPlanned() {
        val all = AdhkarReminders(morning = true, evening = true, sleep = true, sleepMinutes = 22 * 60 + 30)
        val parsed = AdhkarReminderPlan.reminders(all, null, zone, midnight).keys.mapNotNull(AdhkarReminderPlan::parse)
        assertEquals(setOf(AdhkarReminderKind.SLEEP), parsed.map { it.kind }.toSet())
        assertEquals(day.atTime(22, 30).atZone(zone).toInstant(), parsed.minOf { it.at })
        assertTrue(parsed.all { it.kind.chapter == AdhkarBook.SLEEP })
    }

    @Test
    fun pastTimesAndTurnedOffRemindersAreNotPlanned() {
        val evening = day.atTime(23, 0).atZone(zone).toInstant()
        val plan = AdhkarReminderPlan.reminders(AdhkarReminders(sleep = true), place, zone, evening)
        assertEquals(2, plan.size) // today's 22:00 has passed
        assertTrue(AdhkarReminderPlan.reminders(AdhkarReminders(), place, zone, midnight).isEmpty())
    }

    @Test
    fun keysRoundTripAndForeignKeysAreIgnored() {
        val r = AdhkarReminderPlan.Reminder(AdhkarReminderKind.EVENING, midnight)
        assertEquals(r, AdhkarReminderPlan.parse(AdhkarReminderPlan.key(r)))
        assertNull(AdhkarReminderPlan.parse("adhan|FAJR|SOUND|1"))
        assertNull(AdhkarReminderPlan.parse("adhkar|NOON|1"))
    }

    private lateinit var scope: CoroutineScope
    private lateinit var location: LocationStore
    private lateinit var store: AdhkarRemindersStore
    private lateinit var alarms: AdhkarReminderAlarms
    private val scheduler get() = AlarmScheduler.get(context)
    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = makkah.takeIf { it.cityId == cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        location = LocationStore({ File(tmp.root, "location.json") }, scope)
        store = AdhkarRemindersStore({ File(tmp.root, "reminders.json") }, scope)
        val prayer = PrayerPreferencesStore({ File(tmp.root, "prayer.json") }, scope)
        alarms = AdhkarReminderAlarms({ places }, location, prayer, store, clock = { Clock.fixed(midnight, zone) })
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        scheduler.cancelAll(AlarmKind.ADHKAR)
        scope.cancel()
    }

    @Test
    fun alarmsFollowTheSettingsAndStopWhenAllAreOff() = runBlocking {
        location.save(SavedLocation(LocationSource.CITY, makkah.cityId, makkah.latitude, makkah.longitude, zone.id, 1))
        store.update { it.copy(morning = true, sleep = true) }
        alarms.replan(context, ReplanReason.SETTINGS_CHANGED)
        val kinds = scheduler.scheduled().filter { it.kind == AlarmKind.ADHKAR }
            .mapNotNull { AdhkarReminderPlan.parse(it.key)?.kind }
        val expected = mapOf(AdhkarReminderKind.MORNING to 3, AdhkarReminderKind.SLEEP to 3)
        assertEquals(expected, kinds.groupingBy { it }.eachCount())

        store.update { AdhkarReminders() }
        alarms.replan(context, ReplanReason.SETTINGS_CHANGED)
        assertTrue(scheduler.scheduled().none { it.kind == AlarmKind.ADHKAR })
    }

    @Test
    fun aFiredReminderOpensItsChapterAndALateOneIsDropped() {
        val reminder = AdhkarReminderPlan.Reminder(AdhkarReminderKind.SLEEP, midnight)
        assertTrue(AdhkarReminderNotifier.post(context, reminder, midnight.plusSeconds(60)))
        val posted = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications.single()
        val intent = shadowOf(posted.contentIntent).savedIntent
        assertEquals(AdhkarBook.SLEEP, intent.getIntExtra(AdhkarReminderNotifier.EXTRA_CHAPTER, -1))
        val late = midnight.plus(AdhkarReminderNotifier.STALE_AFTER).plusSeconds(60)
        assertFalse(AdhkarReminderNotifier.post(context, reminder, late))
    }

    @Test
    fun screenAsksForALocationOnlyWhenMorningOrEveningNeedIt() {
        val toggled = mutableListOf<Boolean>()
        val actions = object : AdhkarRemindersActions {
            override val onBack = {}
            override val onMorning: (Boolean) -> Unit = { toggled += it }
            override val onEvening: (Boolean) -> Unit = {}
            override val onSleep: (Boolean) -> Unit = {}
            override val onSleepTime: (Int, Int) -> Unit = { _, _ -> }
            override val onChooseLocation = {}
            override val onFixAlerts = {}
        }
        rule.setContent {
            ZikraTheme {
                AdhkarRemindersScreen(
                    AdhkarRemindersUiState(true, AdhkarReminders(morning = true, sleep = true), hasLocation = false),
                    actions,
                )
            }
        }
        rule.onNodeWithTag(REMINDER_NEEDS_LOCATION_TAG).assertExists()
        rule.onNodeWithTag(REMINDER_SLEEP_TAG).assertIsOn()
        rule.onNodeWithTag(REMINDER_MORNING_TAG).performClick()
        assertEquals(listOf(false), toggled)
    }
}
