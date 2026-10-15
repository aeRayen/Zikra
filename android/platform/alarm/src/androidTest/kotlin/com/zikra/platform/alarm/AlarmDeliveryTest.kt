package com.zikra.platform.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** WP 2.4 on a real Android runtime: a scheduled alarm is delivered by the system to our handler. */
@RunWith(AndroidJUnit4::class)
class AlarmDeliveryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
    }

    @After
    fun tearDown() {
        AlarmScheduler.get(context).cancelAll(AlarmKind.ADHAN)
        AlarmRegistrations.clearForTest()
    }

    @Test
    fun exactAlarmFiresAndReachesTheHandler() {
        // Android 14 denies exact alarms to new installs by default; a user would allow it in settings.
        shell("appops set ${context.packageName} SCHEDULE_EXACT_ALARM allow")
        val fired = CountDownLatch(1)
        val keys = mutableListOf<String>()
        AlarmRegistrations.register(AlarmKind.ADHAN) { _, key ->
            keys += key
            fired.countDown()
        }
        val scheduler = AlarmScheduler.get(context)
        assertEquals(DeliveryMode.EXACT, scheduler.deliveryMode())
        scheduler.replaceAll(AlarmKind.ADHAN, mapOf("test:fajr" to Instant.now().plusSeconds(3)))

        assertTrue("alarm not delivered within 30 s", fired.await(30, TimeUnit.SECONDS))
        assertEquals(listOf("test:fajr"), keys)
        assertTrue("fired alarm must leave the registry", scheduler.scheduled().isEmpty())
    }
}
