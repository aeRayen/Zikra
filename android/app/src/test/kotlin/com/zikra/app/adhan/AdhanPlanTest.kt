package com.zikra.app.adhan

import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class AdhanPlanTest {

    private val zone = ZoneId.of("Asia/Riyadh")
    private val makkah = GeoPoint(21.42664, 39.82563)
    private val settings = PrayerSettings(CalculationMethodId.UMM_AL_QURA)
    private val noon: Instant = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, zone).toInstant()

    private fun plan(alerts: Map<String, String> = emptyMap(), now: Instant = noon) =
        AdhanPlan.alerts(makkah, zone, settings, alerts, now).keys.map { requireNotNull(AdhanPlan.parse(it)) }

    @Test
    fun defaultsAlertTheFivePrayersWithSoundForThreeDaysAndSkipSunrise() {
        val alerts = plan()
        val today = noon.atZone(zone).toLocalDate()
        val expected = (0L until AdhanPlan.DAYS_AHEAD).sumOf { d ->
            val day = PrayerCalculator.compute(makkah, today.plusDays(d), zone, settings)
            PrayerName.entries.count { it != PrayerName.SUNRISE && day[it].isAfter(noon) }
        }
        assertEquals(4 + 5 + 5, expected) // at noon in Makkah: Dhuhr, Asr, Maghrib, Isha remain today
        assertEquals(expected, alerts.size)
        assertTrue(alerts.none { it.prayer == PrayerName.SUNRISE })
        assertTrue(alerts.all { it.mode == AlertMode.SOUND && it.at.isAfter(noon) })
    }

    @Test
    fun alertTimesAreTheEngineTimes() {
        val dhuhr = plan().first { it.prayer == PrayerName.DHUHR }
        val engine = PrayerCalculator.compute(makkah, noon.atZone(zone).toLocalDate(), zone, settings)
        assertEquals(engine[PrayerName.DHUHR], dhuhr.at)
    }

    @Test
    fun perPrayerModesAreRespected() {
        val alerts = plan(mapOf("FAJR" to "OFF", "ISHA" to "SILENT", "SUNRISE" to "SOUND"))
        assertTrue(alerts.none { it.prayer == PrayerName.FAJR })
        assertTrue(alerts.filter { it.prayer == PrayerName.ISHA }.all { it.mode == AlertMode.SILENT })
        assertTrue(alerts.any { it.prayer == PrayerName.SUNRISE && it.mode == AlertMode.SOUND })
    }

    @Test
    fun unknownStoredModeFallsBackToTheDefault() {
        assertEquals(AlertMode.SOUND, AlertMode.resolve(PrayerName.ASR, mapOf("ASR" to "VIBRATE_ONLY")))
        assertEquals(AlertMode.OFF, AlertMode.resolve(PrayerName.SUNRISE, emptyMap()))
    }

    @Test
    fun keysRoundTripAndForeignKeysAreRejected() {
        val alert = AdhanPlan.Alert(PrayerName.MAGHRIB, AlertMode.SILENT, Instant.ofEpochMilli(1_780_000_000_000))
        assertEquals(alert, AdhanPlan.parse(AdhanPlan.key(alert)))
        assertNull(AdhanPlan.parse("alarm|wake|1"))
        assertNull(AdhanPlan.parse("adhan|MAGHRIB|LOUD|1"))
        assertNull(AdhanPlan.parse("adhan|MAGHRIB|SOUND|soon"))
        assertNull(AdhanPlan.parse(""))
    }
}
