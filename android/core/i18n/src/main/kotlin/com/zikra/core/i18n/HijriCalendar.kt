package com.zikra.core.i18n

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import java.util.Locale

/** A date in the Umm al-Qura Hijri calendar. [month] is 1 (Muharram) .. 12 (Dhu al-Hijjah). */
data class HijriDate(val year: Int, val month: Int, val day: Int)

/**
 * Hijri dates (WP 2.7) from java.time's HijrahChronology, which is the Umm al-Qura calendar of Saudi
 * Arabia (valid roughly 1300-1600 AH). The user may shift it by up to ±2 days to follow local moon
 * sighting. The date follows the civil (midnight) day.
 */
object HijriCalendar {
    const val MAX_ADJUSTMENT_DAYS = 2

    /** null outside the calendar's data range (about 1882-2174 CE). */
    fun fromGregorian(date: LocalDate, adjustmentDays: Int = 0): HijriDate? {
        val shift = adjustmentDays.coerceIn(-MAX_ADJUSTMENT_DAYS, MAX_ADJUSTMENT_DAYS)
        return try {
            val h = HijrahDate.from(date.plusDays(shift.toLong()))
            HijriDate(h.get(ChronoField.YEAR_OF_ERA), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * "14 Ramadan 1447 AH" / "١٤ رمضان ١٤٤٧ هـ": month and era names from the locale data (CLDR), so every
     * language the platform knows is covered; digits from [numerals].
     */
    fun format(date: HijriDate, locale: Locale, numerals: NumeralSystem): String {
        val hijrah = HijrahChronology.INSTANCE.date(date.year, date.month, date.day)
        val formatter = DateTimeFormatter.ofPattern(PATTERN, locale).withChronology(HijrahChronology.INSTANCE)
        return numerals.apply(NumeralSystem.toLatin(formatter.format(hijrah)))
    }

    private const val PATTERN = "d MMMM y G"
}
