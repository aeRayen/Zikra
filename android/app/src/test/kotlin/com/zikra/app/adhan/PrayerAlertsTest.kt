package com.zikra.app.adhan

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.R
import com.zikra.core.places.NearestPlace
import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerAlertsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext<Application>()
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val zone = ZoneId.of("Asia/Riyadh")

    // In the future: the real scheduler ignores past times by the real clock. Dhuhr in Makkah is always after noon.
    private val noon: Instant = LocalDate.now(zone).plusDays(10).atTime(12, 0).atZone(zone).toInstant()
    private val makkah = Place(104515, "Makkah", null, "Saudi Arabia", "SA", 21.42664, 39.82563, zone)
    private val places = object : PlaceLookup {
        override fun search(query: String, language: String, limit: Int) = emptyList<Place>()
        override fun byId(cityId: Long, language: String) = makkah.takeIf { it.cityId == cityId }
        override fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? = null
    }

    private lateinit var scope: CoroutineScope
    private lateinit var location: LocationStore
    private lateinit var prefs: PrayerPreferencesStore
    private lateinit var alerts: PrayerAlerts
    private val scheduler get() = AlarmScheduler.get(context)

    @Before
    fun setUp() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        location = LocationStore({ File(tmp.root, "location.json") }, scope)
        prefs = PrayerPreferencesStore({ File(tmp.root, "prayer.json") }, scope)
        alerts = PrayerAlerts({ places }, location, prefs, clock = { Clock.fixed(noon, zone) })
        shadowOf(notifications).setNotificationsEnabled(true)
    }

    @After
    fun tearDown() {
        scheduler.cancelAll(AlarmKind.ADHAN)
        scope.cancel()
    }

    private fun alert(prayer: PrayerName, mode: AlertMode, at: Instant = noon) = AdhanPlan.Alert(prayer, mode, at)

    @Test
    fun noLocationMeansNoAlerts() = runBlocking {
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("left-over" to noon.plusSeconds(3600)))
        alerts.replan(context, ReplanReason.BOOT)
        assertTrue(scheduler.scheduled().none { it.kind == AlarmKind.ADHAN })
    }

    @Test
    fun savedLocationSchedulesThreeDaysAndRespectsTheModes() = runBlocking {
        location.save(SavedLocation(LocationSource.CITY, makkah.cityId, makkah.latitude, makkah.longitude, zone.id, 1))
        alerts.replan(context, ReplanReason.SETTINGS_CHANGED)
        val planned = scheduler.scheduled().mapNotNull { AdhanPlan.parse(it.key) }
        assertEquals(14, planned.size) // noon in Makkah: 4 left today + 5 + 5
        prefs.update { it.copy(alerts = mapOf("FAJR" to "OFF")) }
        alerts.replan(context, ReplanReason.SETTINGS_CHANGED)
        val withoutFajr = scheduler.scheduled().mapNotNull { AdhanPlan.parse(it.key) }
        assertEquals(12, withoutFajr.size)
        assertTrue(withoutFajr.none { it.prayer == PrayerName.FAJR })
    }

    @Test
    fun soundAlertUsesTheSoundChannelAndNamesThePrayer() {
        assertTrue(PrayerAlertNotifier.post(context, alert(PrayerName.DHUHR, AlertMode.SOUND), noon, zone))
        val posted = shadowOf(notifications).allNotifications.single()
        assertEquals(PrayerAlertNotifier.CHANNEL_SOUND, posted.channelId)
        val title = posted.extras.getString(NotificationCompat.EXTRA_TITLE)
        assertEquals(context.getString(R.string.alert_prayer_title, context.getString(R.string.prayer_dhuhr)), title)
    }

    @Test
    fun silentAlertUsesTheSilentChannelWithoutSound() {
        PrayerAlertNotifier.post(context, alert(PrayerName.ISHA, AlertMode.SILENT), noon, zone)
        assertEquals(PrayerAlertNotifier.CHANNEL_SILENT, shadowOf(notifications).allNotifications.single().channelId)
        val channel = notifications.getNotificationChannel(PrayerAlertNotifier.CHANNEL_SILENT)
        assertEquals(null, channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    @Test
    fun lateAlertIsNotShown() {
        val late = noon.plus(PrayerAlertNotifier.STALE_AFTER).plusSeconds(60)
        assertFalse(PrayerAlertNotifier.post(context, alert(PrayerName.DHUHR, AlertMode.SOUND), late, zone))
        assertTrue(shadowOf(notifications).allNotifications.isEmpty())
    }

    @Test
    fun nothingIsPostedWhenNotificationsAreOff() {
        shadowOf(notifications).setNotificationsEnabled(false)
        assertFalse(PrayerAlertNotifier.post(context, alert(PrayerName.ASR, AlertMode.SOUND), noon, zone))
    }

    @Test
    fun firedAlertPostsAndRollsThePlanForward() = runBlocking {
        location.save(SavedLocation(LocationSource.CITY, makkah.cityId, makkah.latitude, makkah.longitude, zone.id, 1))
        alerts.onAlarm(context, AdhanPlan.key(alert(PrayerName.DHUHR, AlertMode.SOUND)))
        assertEquals(1, shadowOf(notifications).allNotifications.size)
        assertEquals(14, scheduler.scheduled().count { it.kind == AlarmKind.ADHAN })
    }

    @Test
    @Config(qualifiers = "ar")
    fun channelNamesFollowTheLanguage() {
        PrayerAlertNotifier.ensureChannels(context)
        assertEquals(
            context.getString(R.string.channel_prayer_sound),
            notifications.getNotificationChannel(PrayerAlertNotifier.CHANNEL_SOUND).name,
        )
        assertTrue(context.getString(R.string.channel_prayer_sound).contains("الصلاة"))
    }
}
