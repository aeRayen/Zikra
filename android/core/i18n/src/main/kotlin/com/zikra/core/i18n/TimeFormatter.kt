package com.zikra.core.i18n

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formats clock times for prayer times and alarms (I18N_SPEC_AR.md §5).
 * [use24Hour] comes from the device setting (DateFormat.is24HourFormat on Android); the day-period
 * marker (AM/PM, ص/م) comes from the locale; digits come from [numerals].
 */
object TimeFormatter {
    private val formatters = mutableMapOf<Pair<Locale, Boolean>, DateTimeFormatter>()

    fun format(time: LocalTime, locale: Locale, use24Hour: Boolean, numerals: NumeralSystem): String {
        val formatter = synchronized(formatters) {
            formatters.getOrPut(locale to use24Hour) {
                DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale)
            }
        }
        // Format with Western digits, then substitute: identical output on JVM/Android/iOS.
        return numerals.apply(NumeralSystem.toLatin(formatter.format(time)))
    }
}
