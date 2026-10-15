package com.zikra.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

class NumeralAndTimeTest {

    @Test
    fun substitutesDigitsOnly() {
        assertEquals("١٥:٢٠", NumeralSystem.ARAB.apply("15:20"))
        assertEquals("۱۵:۲۰", NumeralSystem.ARABEXT.apply("15:20"))
        assertEquals("15:20", NumeralSystem.LATN.apply("15:20"))
        assertEquals("آية ٢٥٥", NumeralSystem.ARAB.apply("آية 255"))
    }

    @Test
    fun roundTripsToLatin() {
        assertEquals("0123456789", NumeralSystem.toLatin(NumeralSystem.ARAB.apply("0123456789")))
        assertEquals("0123456789", NumeralSystem.toLatin(NumeralSystem.ARABEXT.apply("0123456789")))
    }

    @Test
    fun defaultsFollowAdr019() {
        assertEquals(NumeralSystem.ARAB, NumeralSystem.resolve(NumeralSystem.AUTO, "ar-EG"))
        assertEquals(NumeralSystem.ARABEXT, NumeralSystem.resolve(null, "ur"))
        assertEquals(NumeralSystem.ARABEXT, NumeralSystem.resolve("auto", "fa"))
        assertEquals(NumeralSystem.LATN, NumeralSystem.resolve(null, "en"))
        assertEquals(NumeralSystem.LATN, NumeralSystem.resolve(null, "id"))
    }

    @Test
    fun userPreferenceOverridesLanguageDefault() {
        assertEquals(NumeralSystem.LATN, NumeralSystem.resolve("latn", "ar"))
        assertEquals(NumeralSystem.ARAB, NumeralSystem.resolve("arab", "en"))
    }

    @Test
    fun formats24HourTimeWithLocaleDigits() {
        val t = LocalTime.of(15, 20)
        assertEquals("15:20", TimeFormatter.format(t, Locale.ENGLISH, use24Hour = true, numerals = NumeralSystem.LATN))
        assertEquals("١٥:٢٠", TimeFormatter.format(t, Locale("ar"), use24Hour = true, numerals = NumeralSystem.ARAB))
    }

    @Test
    fun formats12HourTimeWithLocalizedDayPeriod() {
        val t = LocalTime.of(15, 20)
        val en = TimeFormatter.format(t, Locale.ENGLISH, use24Hour = false, numerals = NumeralSystem.LATN)
        assertEquals("3:20 PM", en)
        val ar = TimeFormatter.format(t, Locale("ar"), use24Hour = false, numerals = NumeralSystem.ARAB)
        assertTrue("got: $ar", ar.startsWith("٣:٢٠"))
        assertTrue("Arabic day period expected, got: $ar", !ar.contains("PM"))
    }

    @Test
    fun compactFormatDropsTheDayPeriodOnly() {
        val t = LocalTime.of(15, 5)
        assertEquals("3:05", TimeFormatter.formatCompact(t, use24Hour = false, numerals = NumeralSystem.LATN))
        assertEquals("15:05", TimeFormatter.formatCompact(t, use24Hour = true, numerals = NumeralSystem.LATN))
        assertEquals("٣:٠٥", TimeFormatter.formatCompact(t, use24Hour = false, numerals = NumeralSystem.ARAB))
        assertEquals(
            "۰۴:۱۱",
            TimeFormatter.formatCompact(LocalTime.of(4, 11), use24Hour = true, numerals = NumeralSystem.ARABEXT)
        )
    }
}
