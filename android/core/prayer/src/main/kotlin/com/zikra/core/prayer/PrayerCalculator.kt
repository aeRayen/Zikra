package com.zikra.core.prayer

import com.batoulapps.adhan2.CalculationParameters
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerAdjustments
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.data.DateComponents
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import com.batoulapps.adhan2.HighLatitudeRule as EngineHighLatitudeRule
import com.batoulapps.adhan2.Madhab as EngineMadhab

/** Times of one day at one place. All instants are absolute; format them in [zone] for display. */
data class DayPrayerTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val times: Map<PrayerName, Instant>,
) {
    operator fun get(prayer: PrayerName): Instant = times.getValue(prayer)
}

data class UpcomingPrayer(val prayer: PrayerName, val at: Instant)

/**
 * Prayer time calculation, fully offline (WP 2.2). Wraps the Adhan engine so that the rest of the
 * app only sees our types and java.time, and so the engine can be replaced without touching callers.
 */
object PrayerCalculator {

    /**
     * Times for [date] as observed in [zone]. The engine works on the civil date, so DST and
     * time-zone changes are handled by converting instants with the zone rules.
     */
    fun compute(place: GeoPoint, date: LocalDate, zone: ZoneId, settings: PrayerSettings): DayPrayerTimes {
        val coordinates = Coordinates(place.latitude, place.longitude)
        val times =
            PrayerTimes(
                coordinates,
                DateComponents(date.year, date.monthValue, date.dayOfMonth),
                parameters(coordinates, settings)
            )
        fun instant(i: kotlinx.datetime.Instant) = Instant.ofEpochSecond(i.epochSeconds, i.nanosecondsOfSecond.toLong())
        return DayPrayerTimes(
            date = date,
            zone = zone,
            times = mapOf(
                PrayerName.FAJR to instant(times.fajr),
                PrayerName.SUNRISE to instant(times.sunrise),
                PrayerName.DHUHR to instant(times.dhuhr),
                PrayerName.ASR to instant(times.asr),
                PrayerName.MAGHRIB to instant(times.maghrib),
                PrayerName.ISHA to instant(times.isha),
            ),
        )
    }

    /** The next prayer after [now] (sunrise excluded: it is not a prayer), rolling over to tomorrow's Fajr. */
    fun next(place: GeoPoint, now: Instant, zone: ZoneId, settings: PrayerSettings): UpcomingPrayer {
        val today = now.atZone(zone).toLocalDate()
        val prayersToday = compute(place, today, zone, settings).times.filterKeys { it != PrayerName.SUNRISE }
        prayersToday.entries.sortedBy { it.value }.firstOrNull { it.value.isAfter(now) }?.let {
            return UpcomingPrayer(it.key, it.value)
        }
        val tomorrowFajr = compute(place, today.plusDays(1), zone, settings)[PrayerName.FAJR]
        return UpcomingPrayer(PrayerName.FAJR, tomorrowFajr)
    }

    /** Qibla bearing in degrees clockwise from TRUE north (the compass adds magnetic declination). */
    fun qiblaBearing(place: GeoPoint): Double = Qibla(Coordinates(place.latitude, place.longitude)).direction

    private fun parameters(coordinates: Coordinates, settings: PrayerSettings): CalculationParameters {
        val adjust = settings.adjustmentMinutes
        return settings.method.engine.parameters.copy(
            madhab = if (settings.madhab == AsrMadhab.HANAFI) EngineMadhab.HANAFI else EngineMadhab.SHAFI,
            highLatitudeRule = when (settings.highLatitudeRule) {
                null -> EngineHighLatitudeRule.recommendedFor(coordinates)
                HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> EngineHighLatitudeRule.MIDDLE_OF_THE_NIGHT
                HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> EngineHighLatitudeRule.SEVENTH_OF_THE_NIGHT
                HighLatitudeRule.TWILIGHT_ANGLE -> EngineHighLatitudeRule.TWILIGHT_ANGLE
            },
            prayerAdjustments = PrayerAdjustments(
                fajr = adjust[PrayerName.FAJR] ?: 0,
                sunrise = adjust[PrayerName.SUNRISE] ?: 0,
                dhuhr = adjust[PrayerName.DHUHR] ?: 0,
                asr = adjust[PrayerName.ASR] ?: 0,
                maghrib = adjust[PrayerName.MAGHRIB] ?: 0,
                isha = adjust[PrayerName.ISHA] ?: 0,
            ),
        )
    }
}
