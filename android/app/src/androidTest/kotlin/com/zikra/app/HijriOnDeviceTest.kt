package com.zikra.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zikra.core.i18n.HijriCalendar
import com.zikra.core.i18n.HijriDate
import com.zikra.core.i18n.NumeralSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.Locale

/**
 * Android ships its own copy of java.time's Umm al-Qura data: check the device agrees with the
 * published month starts verified on the JVM (HijriCalendarTest), and that formatting works there.
 */
@RunWith(AndroidJUnit4::class)
class HijriOnDeviceTest {

    @Test
    fun deviceCalendarMatchesPublishedMonthStarts() {
        val starts = mapOf(
            "2024-03-11" to HijriDate(1445, 9, 1),
            "2025-03-01" to HijriDate(1446, 9, 1),
            "2025-03-30" to HijriDate(1446, 10, 1),
            "2025-06-26" to HijriDate(1447, 1, 1),
            "2026-02-18" to HijriDate(1447, 9, 1),
        )
        starts.forEach { (gregorian, hijri) ->
            assertEquals(gregorian, hijri, HijriCalendar.fromGregorian(LocalDate.parse(gregorian)))
        }
    }

    @Test
    fun formatsInArabicAndEnglishOnTheDevice() {
        val date = HijriDate(1447, 9, 1)
        val ar = HijriCalendar.format(date, Locale("ar"), NumeralSystem.ARAB)
        val en = HijriCalendar.format(date, Locale.ENGLISH, NumeralSystem.LATN)
        assertTrue(ar, ar.contains("١٤٤٧"))
        assertTrue(en, en.contains("1447"))
    }
}
