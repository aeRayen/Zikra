package com.zikra.platform.alarm

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.UserManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
import org.robolectric.shadows.ShadowAlarmManager
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlarmSchedulerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext<Application>()
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val shadowAlarms get() = shadowOf(alarmManager)
    private var nowMs = 1_000_000L
    private lateinit var registryFile: File
    private lateinit var scheduler: AlarmScheduler

    private fun at(offsetMs: Long): Instant = Instant.ofEpochMilli(nowMs + offsetMs)

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        registryFile = File(tmp.root, "alarms/registry.json")
        scheduler = AlarmScheduler(context, AlarmRegistry(registryFile), { nowMs })
    }

    @After
    fun tearDown() = AlarmRegistrations.clearForTest()

    @Test
    fun armsExactAlarmsThatFireWhileIdleAndIgnoresPastTimes() {
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("fajr" to at(60_000), "dhuhr" to at(120_000), "past" to at(-1)))
        val scheduled = shadowAlarms.scheduledAlarms
        assertEquals(listOf(nowMs + 60_000, nowMs + 120_000), scheduled.map { it.getTriggerAtMs() }.sorted())
        assertTrue(scheduled.all { it.isAllowWhileIdle && it.getType() == AlarmManager.RTC_WAKEUP })
        assertEquals(DeliveryMode.EXACT, scheduler.deliveryMode())
        assertEquals(setOf("fajr", "dhuhr"), scheduler.scheduled().map { it.key }.toSet())
    }

    @Test
    fun replacingCancelsRemovedAlarmsAndKeepsRequestCodes() {
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("a" to at(60_000), "b" to at(120_000)))
        val codeOfA = scheduler.scheduled().single { it.key == "a" }.requestCode
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("a" to at(90_000)))
        assertEquals(listOf(nowMs + 90_000), shadowAlarms.scheduledAlarms.map { it.getTriggerAtMs() })
        assertEquals(codeOfA, scheduler.scheduled().single().requestCode)
        scheduler.cancelAll(AlarmKind.ADHAN)
        assertTrue(shadowAlarms.scheduledAlarms.isEmpty())
        assertTrue(scheduler.scheduled().isEmpty())
    }

    @Test
    fun kindsAreIndependent() {
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("fajr" to at(60_000)))
        scheduler.replaceAll(AlarmKind.USER_ALARM, mapOf("wake" to at(30_000)))
        scheduler.cancelAll(AlarmKind.ADHAN)
        assertEquals(listOf("wake"), scheduler.scheduled().map { it.key })
        // A user alarm is a real alarm clock: the system shows it as the next alarm.
        assertEquals(nowMs + 30_000, alarmManager.nextAlarmClock?.triggerTime)
    }

    @Test
    fun withoutExactPermissionFallsBackToInexactButStillWhileIdle() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertEquals(DeliveryMode.INEXACT, scheduler.deliveryMode())
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("fajr" to at(60_000)))
        val alarm = shadowAlarms.scheduledAlarms.single()
        assertTrue(alarm.isAllowWhileIdle)
        assertNull(alarmManager.nextAlarmClock)
        assertNotNull(AlarmScheduler.exactAlarmSettingsIntent(context))
    }

    @Test
    fun rebootRearmsFutureAlarmsFromTheRegistryAndDropsPastOnes() {
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("soon" to at(60_000), "later" to at(600_000)))
        // Reboot: the system forgets every alarm; time moves past «soon».
        val saved = registryFile.readText()
        scheduler.cancelAll(AlarmKind.ADHAN)
        registryFile.writeText(saved) // our device-protected file survives the reboot
        assertTrue(shadowAlarms.scheduledAlarms.isEmpty())
        nowMs += 120_000
        val rebooted = AlarmScheduler(context, AlarmRegistry(registryFile), { nowMs })
        rebooted.rearmAll()
        assertEquals(listOf("later"), rebooted.scheduled().map { it.key })
        assertEquals(listOf(nowMs - 120_000 + 600_000), shadowAlarms.scheduledAlarms.map { it.getTriggerAtMs() })
    }

    @Test
    fun firingRemovesTheEntryAndRunsTheHandlerOfItsKind() {
        val fired = java.util.Collections.synchronizedList(mutableListOf<String>())
        val done = java.util.concurrent.CountDownLatch(1)
        AlarmRegistrations.register(AlarmKind.ADHAN) { _, key ->
            fired += key
            done.countDown()
        }
        // The receiver uses the process-wide scheduler (device-protected registry), so schedule through it.
        val shared = AlarmScheduler.get(context)
        shared.replaceAll(AlarmKind.ADHAN, mapOf("maghrib" to Instant.now().plusSeconds(60)))
        val entry = shared.scheduled().single()
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmScheduler.ACTION_FIRE)
            .putExtra(AlarmScheduler.EXTRA_KEY, entry.key)
            .putExtra(AlarmScheduler.EXTRA_KIND, entry.kind.name)
            .putExtra(AlarmScheduler.EXTRA_REQUEST_CODE, entry.requestCode)
        AlarmReceiver().onReceive(context, intent)
        assertTrue(done.await(5, java.util.concurrent.TimeUnit.SECONDS)) // handler runs off the main thread
        assertEquals(listOf("maghrib"), fired)
        assertTrue(shared.scheduled().isEmpty())
        shared.cancelAll(AlarmKind.ADHAN)
    }

    @Test
    fun systemEventsMapToReplanReasons() {
        assertEquals(ReplanReason.BOOT, AlarmSystemEventsReceiver.reasonFor(Intent.ACTION_LOCKED_BOOT_COMPLETED))
        assertEquals(ReplanReason.BOOT, AlarmSystemEventsReceiver.reasonFor(Intent.ACTION_BOOT_COMPLETED))
        assertEquals(
            ReplanReason.TIME_ZONE_CHANGED,
            AlarmSystemEventsReceiver.reasonFor(Intent.ACTION_TIMEZONE_CHANGED)
        )
        assertEquals(ReplanReason.TIME_CHANGED, AlarmSystemEventsReceiver.reasonFor(Intent.ACTION_TIME_CHANGED))
        assertEquals(ReplanReason.APP_UPDATED, AlarmSystemEventsReceiver.reasonFor(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertEquals(
            ReplanReason.PERMISSION_CHANGED,
            AlarmSystemEventsReceiver.reasonFor(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED),
        )
        assertNull(AlarmSystemEventsReceiver.reasonFor("some.other.ACTION"))
    }

    @Test
    fun plannersRunOnlyOnceTheUserHasUnlocked() = runBlocking {
        val reasons = mutableListOf<ReplanReason>()
        AlarmRegistrations.register(AlarmPlanner { _, reason -> reasons += reason })
        val users = shadowOf(context.getSystemService(UserManager::class.java))

        users.setUserUnlocked(false) // LOCKED_BOOT_COMPLETED: settings are not readable yet
        AlarmSystemEventsReceiver.onSystemEvent(context, ReplanReason.BOOT)
        assertTrue(reasons.isEmpty())

        users.setUserUnlocked(true)
        AlarmSystemEventsReceiver.onSystemEvent(context, ReplanReason.BOOT)
        AlarmSystemEventsReceiver.onSystemEvent(context, ReplanReason.TIME_ZONE_CHANGED)
        assertEquals(listOf(ReplanReason.BOOT, ReplanReason.TIME_ZONE_CHANGED), reasons)
    }
}
