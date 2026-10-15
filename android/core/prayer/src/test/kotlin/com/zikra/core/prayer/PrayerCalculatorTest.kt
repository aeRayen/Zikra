package com.zikra.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class PrayerCalculatorTest {

    private val london = GeoPoint(51.507194, -0.116711)
    private val londonZone = ZoneId.of("Europe/London")
    private val settings = PrayerSettings(CalculationMethodId.MUSLIM_WORLD_LEAGUE)

    @Test
    fun prayersAreInDayOrder() {
        val day = PrayerCalculator.compute(london, LocalDate.of(2026, 3, 1), londonZone, settings)
        val ordered = PrayerName.entries.map { day[it] }
        assertEquals(ordered.sorted(), ordered)
    }

    @Test
    fun daylightSavingChangeShiftsLocalClockByAboutAnHour() {
        // UK clocks go forward on 2026-03-29. Dhuhr in local time jumps about +1h across that night.
        val before = PrayerCalculator.compute(london, LocalDate.of(2026, 3, 28), londonZone, settings)
        val after = PrayerCalculator.compute(london, LocalDate.of(2026, 3, 29), londonZone, settings)
        val localBefore = before[PrayerName.DHUHR].atZone(londonZone).toLocalTime()
        val localAfter = after[PrayerName.DHUHR].atZone(londonZone).toLocalTime()
        val shift = Duration.between(localBefore, localAfter).toMinutes()
        assertTrue("shift was $shift min", shift in 58..62)
    }

    @Test
    fun nextPrayerIsTodaysWhenOneRemains() {
        val date = LocalDate.of(2026, 6, 1)
        val day = PrayerCalculator.compute(london, date, londonZone, settings)
        val justAfterDhuhr = day[PrayerName.DHUHR].plusSeconds(60)
        val next = PrayerCalculator.next(london, justAfterDhuhr, londonZone, settings)
        assertEquals(UpcomingPrayer(PrayerName.ASR, day[PrayerName.ASR]), next)
    }

    @Test
    fun nextPrayerSkipsSunrise() {
        val date = LocalDate.of(2026, 6, 1)
        val day = PrayerCalculator.compute(london, date, londonZone, settings)
        val afterFajr = day[PrayerName.FAJR].plusSeconds(60)
        assertEquals(PrayerName.DHUHR, PrayerCalculator.next(london, afterFajr, londonZone, settings).prayer)
    }

    @Test
    fun nextPrayerAfterIshaRollsOverToTomorrowsFajr() {
        val now = ZonedDateTime.of(LocalDate.of(2026, 6, 1), LocalTime.of(23, 59), londonZone).toInstant()
        val tomorrow = PrayerCalculator.compute(london, LocalDate.of(2026, 6, 2), londonZone, settings)
        assertEquals(
            UpcomingPrayer(PrayerName.FAJR, tomorrow[PrayerName.FAJR]),
            PrayerCalculator.next(london, now, londonZone, settings)
        )
    }

    @Test
    fun adjustmentsMoveOnlyTheirPrayer() {
        val date = LocalDate.of(2026, 6, 1)
        val base = PrayerCalculator.compute(london, date, londonZone, settings)
        val adjusted = PrayerCalculator.compute(
            london,
            date,
            londonZone,
            settings.copy(adjustmentMinutes = mapOf(PrayerName.MAGHRIB to 3))
        )
        assertEquals(Duration.ofMinutes(3), Duration.between(base[PrayerName.MAGHRIB], adjusted[PrayerName.MAGHRIB]))
        assertEquals(base[PrayerName.ISHA], adjusted[PrayerName.ISHA])
    }

    @Test
    fun hanafiAsrIsLater() {
        val date = LocalDate.of(2026, 6, 1)
        val standard = PrayerCalculator.compute(london, date, londonZone, settings)
        val hanafi = PrayerCalculator.compute(london, date, londonZone, settings.copy(madhab = AsrMadhab.HANAFI))
        assertTrue(hanafi[PrayerName.ASR].isAfter(standard[PrayerName.ASR]))
    }

    @Test
    fun invalidInputIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { GeoPoint(91.0, 0.0) }
        assertThrows(IllegalArgumentException::class.java) {
            PrayerSettings(CalculationMethodId.EGYPTIAN, adjustmentMinutes = mapOf(PrayerName.FAJR to 31))
        }
    }

    @Test
    fun methodIdsAreStableAndUnique() {
        val ids = CalculationMethodId.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(CalculationMethodId.UMM_AL_QURA, CalculationMethodId.fromId("umm_al_qura"))
        assertEquals(null, CalculationMethodId.fromId("tehran"))
    }

    @Test
    fun qiblaMatchesIndependentGreatCircleBearing() {
        val places = listOf(
            GeoPoint(40.7128, -74.0060), // New York
            GeoPoint(51.507194, -0.116711), // London
            GeoPoint(-6.2088, 106.8456), // Jakarta
            GeoPoint(30.0444, 31.2357), // Cairo
            GeoPoint(-33.8688, 151.2093), // Sydney
        )
        for (p in places) {
            assertEquals("bearing at $p", greatCircleBearingToKaaba(p), PrayerCalculator.qiblaBearing(p), 0.05)
        }
        assertEquals(58.48, PrayerCalculator.qiblaBearing(places.first()), 0.1)
    }

    private fun greatCircleBearingToKaaba(p: GeoPoint): Double {
        val kaabaLat = Math.toRadians(21.4225241)
        val kaabaLon = Math.toRadians(39.8261818)
        val lat = Math.toRadians(p.latitude)
        val dLon = kaabaLon - Math.toRadians(p.longitude)
        val bearing = atan2(sin(dLon), cos(lat) * kotlin.math.tan(kaabaLat) - sin(lat) * cos(dLon))
        return (Math.toDegrees(bearing) + 360) % 360
    }
}
