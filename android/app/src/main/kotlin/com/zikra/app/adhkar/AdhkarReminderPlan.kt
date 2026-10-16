package com.zikra.app.adhkar

import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import com.zikra.core.settings.AdhkarReminders
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** Which adhkar a reminder is for, and the chapter it opens. */
enum class AdhkarReminderKind(val chapter: Int) {
    MORNING(com.zikra.core.adhkar.AdhkarBook.MORNING_EVENING),
    EVENING(com.zikra.core.adhkar.AdhkarBook.MORNING_EVENING),
    SLEEP(com.zikra.core.adhkar.AdhkarBook.SLEEP),
}

/** Where and how the prayer times are computed; null when no location is saved. */
data class PrayerPlace(val point: GeoPoint, val zone: ZoneId, val settings: PrayerSettings)

/**
 * The reminders to schedule (WP 2.14c, ADR-043). Like prayer alerts, everything a fired alarm needs is
 * in its key, because it may fire before the first unlock after a reboot: "adhkar|<KIND>|<epochMillis>".
 *
 * - Morning: [AFTER_PRAYER] after Fajr; evening: [AFTER_PRAYER] after Asr (the times of these adhkar
 *   begin after those prayers). Without a saved location there are no prayer times, so none: never a guess.
 * - Sleep: at the chosen time, in the phone's time zone.
 */
object AdhkarReminderPlan {
    const val DAYS_AHEAD = 3L
    val AFTER_PRAYER: Duration = Duration.ofMinutes(30)
    private const val PREFIX = "adhkar"
    private const val SEPARATOR = "|"
    private const val KEY_PARTS = 3

    data class Reminder(val kind: AdhkarReminderKind, val at: Instant)

    fun reminders(choice: AdhkarReminders, place: PrayerPlace?, phoneZone: ZoneId, now: Instant): Map<String, Instant> {
        val found = mutableListOf<Reminder>()
        if (place != null && (choice.morning || choice.evening)) {
            val today = now.atZone(place.zone).toLocalDate()
            for (offset in 0 until DAYS_AHEAD) {
                val day = PrayerCalculator.compute(place.point, today.plusDays(offset), place.zone, place.settings)
                if (choice.morning) found += Reminder(AdhkarReminderKind.MORNING, day[PrayerName.FAJR] + AFTER_PRAYER)
                if (choice.evening) found += Reminder(AdhkarReminderKind.EVENING, day[PrayerName.ASR] + AFTER_PRAYER)
            }
        }
        if (choice.sleep) {
            val time = LocalTime.ofSecondOfDay(
                (choice.sleepMinutes.mod(AdhkarReminders.MINUTES_PER_DAY) * SECONDS_PER_MINUTE).toLong(),
            )
            val today = now.atZone(phoneZone).toLocalDate()
            for (offset in 0 until DAYS_AHEAD) {
                val at = today.plusDays(offset).atTime(time).atZone(phoneZone).toInstant()
                found += Reminder(AdhkarReminderKind.SLEEP, at)
            }
        }
        return found.filter { it.at.isAfter(now) }.associate { key(it) to it.at }
    }

    fun key(reminder: Reminder) = listOf(PREFIX, reminder.kind.name, reminder.at.toEpochMilli()).joinToString(SEPARATOR)

    /** null for keys that are not ours or are malformed (e.g. written by a newer version). */
    fun parse(key: String): Reminder? {
        val parts = key.split(SEPARATOR).takeIf { it.size == KEY_PARTS && it[0] == PREFIX } ?: return null
        val kind = AdhkarReminderKind.entries.firstOrNull { it.name == parts[1] }
        val at = parts[2].toLongOrNull()?.let(Instant::ofEpochMilli)
        return if (kind != null && at != null) Reminder(kind, at) else null
    }

    private const val SECONDS_PER_MINUTE = 60
}
