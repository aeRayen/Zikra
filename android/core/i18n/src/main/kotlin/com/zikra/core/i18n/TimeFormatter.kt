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

    /**
     * Compact clock time for dense tables (monthly timetable): 12-hour without the day-period marker
     * ("4:11"), since the column already says which prayer it is; 24-hour unchanged.
     */
    fun formatCompact(time: LocalTime, use24Hour: Boolean, numerals: NumeralSystem): String {
        val text = if (use24Hour) COMPACT_24.format(time) else COMPACT_12.format(time)
        return numerals.apply(text)
    }

    private val COMPACT_24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val COMPACT_12 = DateTimeFormatter.ofPattern("h:mm", Locale.ROOT)
}
