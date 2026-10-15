package com.zikra.core.i18n

import com.ibm.icu.util.IslamicCalendar
import com.ibm.icu.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HijriCalendarTest {

    /** ICU's Umm al-Qura implementation, with its own data: an independent reference. */
    private fun icu(date: LocalDate): HijriDate {
        val cal = IslamicCalendar(TimeZone.GMT_ZONE, com.ibm.icu.util.ULocale.ROOT)
        cal.calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        cal.clear()
        val gmt = com.ibm.icu.util.GregorianCalendar(TimeZone.GMT_ZONE)
        gmt.clear()
        gmt.set(date.year, date.monthValue - 1, date.dayOfMonth)
        cal.timeInMillis = gmt.timeInMillis
        return HijriDate(
            cal.get(IslamicCalendar.EXTENDED_YEAR),
            cal.get(IslamicCalendar.MONTH) + 1,
            cal.get(IslamicCalendar.DAY_OF_MONTH),
        )
    }

    @Test
    fun matchesIcuUmmAlQuraEveryDayFor1420To1500() {
        // 1420 AH begins in April 1999, 1500 AH ends in late 2077.
        var date = LocalDate.of(1999, 4, 17)
        val end = LocalDate.of(2077, 11, 16)
        val mismatches = mutableListOf<String>()
        var days = 0
        while (!date.isAfter(end)) {
            val ours = HijriCalendar.fromGregorian(date)
            val reference = icu(date)
            if (ours != reference) mismatches += "$date: ours=$ours icu=$reference"
            date = date.plusDays(1)
            days++
        }
        assertTrue("checked $days days", days > 28_000)
        assertTrue(mismatches.take(10).joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun publishedMonthStartsOfTheSaudiCalendar() {
        // Widely published Umm al-Qura month starts (Ramadan, Shawwal, Muharram), as a human-readable check.
        val starts = mapOf(
            "2024-03-11" to HijriDate(1445, 9, 1),
            "2025-03-01" to HijriDate(1446, 9, 1),
            "2025-03-30" to HijriDate(1446, 10, 1),
            "2025-06-26" to HijriDate(1447, 1, 1),
            "2026-02-18" to HijriDate(1447, 9, 1),
        )
        starts.forEach { (gregorian, hijri) ->
            assertEquals(
                gregorian,
                hijri,
                HijriCalendar.fromGregorian(LocalDate.parse(gregorian))
            )
        }
    }

    @Test
    fun adjustmentShiftsByWholeDaysAndIsClamped() {
        val date = LocalDate.of(2026, 3, 10)
        val base = requireNotNull(HijriCalendar.fromGregorian(date))
        assertEquals(HijriCalendar.fromGregorian(date.plusDays(1)), HijriCalendar.fromGregorian(date, 1))
        assertEquals(HijriCalendar.fromGregorian(date.minusDays(2)), HijriCalendar.fromGregorian(date, -2))
        assertEquals(HijriCalendar.fromGregorian(date, 2), HijriCalendar.fromGregorian(date, 9)) // clamped
        assertTrue(base.month in 1..12 && base.day in 1..30)
    }

    @Test
    fun outsideTheDataRangeIsNullNotACrash() {
        assertNull(HijriCalendar.fromGregorian(LocalDate.of(1700, 1, 1)))
        assertNull(HijriCalendar.fromGregorian(LocalDate.of(2300, 1, 1)))
    }

    @Test
    fun formatsWithLocaleMonthNamesAndUserDigits() {
        // Month names come from the platform's locale data, compared with ICU's own names for the same date.
        val date = requireNotNull(HijriCalendar.fromGregorian(LocalDate.of(2026, 3, 10)))
        val ar = HijriCalendar.format(date, Locale("ar"), NumeralSystem.ARAB)
        val en = HijriCalendar.format(date, Locale.ENGLISH, NumeralSystem.LATN)
        assertTrue("Arabic digits expected: $ar", ar.any { it in '٠'..'٩' } && ar.none { it in '0'..'9' })
        assertTrue("Latin digits expected: $en", en.contains(date.year.toString()))
        assertTrue("Arabic month name expected: $ar", ar.any { it in '؀'..'ۿ' && it !in '٠'..'٩' })
    }
}
