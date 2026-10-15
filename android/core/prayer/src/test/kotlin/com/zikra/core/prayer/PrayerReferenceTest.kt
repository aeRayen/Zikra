package com.zikra.core.prayer

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * Compares the engine against published official timetables (fixtures from adhan-js, MIT; see
 * resources/reference-times/README.md). Every day of every supported method must match within the
 * fixture's variance.
 */
class PrayerReferenceTest {

    @Serializable
    private data class Params(
        val latitude: Double,
        val longitude: Double,
        val timezone: String,
        val method: String,
        val madhab: String,
        val highLatitudeRule: String,
    )

    @Serializable
    private data class Day(
        val date: String,
        val fajr: String,
        val sunrise: String,
        val dhuhr: String,
        val asr: String,
        val maghrib: String,
        val isha: String,
    )

    @Serializable
    private data class Fixture(val params: Params, val variance: Int = 0, val times: List<Day>)

    private val json = Json { ignoreUnknownKeys = true }
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    private val methods = mapOf(
        "Turkey" to CalculationMethodId.TURKEY,
        "Qatar" to CalculationMethodId.QATAR,
        "Dubai" to CalculationMethodId.DUBAI,
        "Kuwait" to CalculationMethodId.KUWAIT,
        "MoonsightingCommittee" to CalculationMethodId.MOONSIGHTING_COMMITTEE,
        "UmmAlQura" to CalculationMethodId.UMM_AL_QURA,
        "Singapore" to CalculationMethodId.SINGAPORE,
    )

    /** Not offered by the app (see CalculationMethodId); kept in resources for the future engine upgrade. */
    private val unsupported = setOf("Tehran")

    private fun fixtures(): List<Pair<String, Fixture>> {
        val dir = File(requireNotNull(javaClass.getResource("/reference-times")).toURI())
        return dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
            .map { it.name to json.decodeFromString<Fixture>(it.readText()) }
    }

    @Test
    fun everySupportedFixtureDayMatchesTheOfficialTimetable() {
        val all = fixtures()
        assertEquals("fixture files", 8, all.size)
        val supported = all.filter { it.second.params.method !in unsupported }
        val failures = supported.flatMap { (name, fixture) -> mismatches(name, fixture) }
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
        val checkedDays = supported.sumOf { it.second.times.size }
        assertTrue("checked $checkedDays days", checkedDays > 400)
    }

    private fun mismatches(name: String, fixture: Fixture): List<String> {
        val p = fixture.params
        check(p.highLatitudeRule == "MiddleOfTheNight") { "unexpected rule in $name" }
        val settings = PrayerSettings(
            method = requireNotNull(methods[p.method]) { "unmapped method ${p.method}" },
            madhab = if (p.madhab == "Hanafi") AsrMadhab.HANAFI else AsrMadhab.STANDARD,
            highLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
        )
        val zone = ZoneId.of(p.timezone)
        val place = GeoPoint(p.latitude, p.longitude)
        return fixture.times.flatMap { day ->
            val result = PrayerCalculator.compute(place, LocalDate.parse(day.date), zone, settings)
            expected(day).mapNotNull { (prayer, text) ->
                val actual = result[prayer].atZone(zone).toLocalTime()
                val diff = Duration.between(LocalTime.parse(text, clock), actual).toMinutes()
                "$name ${day.date} $prayer expected $text got $actual".takeIf { abs(diff) > fixture.variance }
            }
        }
    }

    private fun expected(day: Day) = mapOf(
        PrayerName.FAJR to day.fajr,
        PrayerName.SUNRISE to day.sunrise,
        PrayerName.DHUHR to day.dhuhr,
        PrayerName.ASR to day.asr,
        PrayerName.MAGHRIB to day.maghrib,
        PrayerName.ISHA to day.isha,
    )
}
