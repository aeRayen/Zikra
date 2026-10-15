package com.zikra.app.adhan

import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.PrayerCalculator
import com.zikra.core.prayer.PrayerName
import com.zikra.core.prayer.PrayerSettings
import java.time.Instant
import java.time.ZoneId

/** How a prayer time is announced. The adhan audio itself waits for a licensed recording (ADR-P05). */
enum class AlertMode {
    OFF,

    /** Notification without sound or vibration. */
    SILENT,

    /** Notification with the phone's notification sound. */
    SOUND,
    ;

    companion object {
        /** The five prayers alert with sound by default; sunrise is off (it is not a prayer). */
        fun default(prayer: PrayerName): AlertMode = if (prayer == PrayerName.SUNRISE) OFF else SOUND

        fun resolve(prayer: PrayerName, stored: Map<String, String>): AlertMode =
            entries.firstOrNull { it.name == stored[prayer.name] } ?: default(prayer)
    }
}

/**
 * Which alerts to schedule. Everything a fired alarm needs is in its key, because the alarm may fire
 * before the first unlock after a reboot, when settings cannot be read (ADR-033).
 * Key: "adhan|<PRAYER>|<MODE>|<epochMillis>".
 */
object AdhanPlan {
    /** Today plus two days: covers a phone that reboots and stays locked for a day. */
    const val DAYS_AHEAD = 3L
    private const val PREFIX = "adhan"
    private const val SEPARATOR = "|"

    data class Alert(val prayer: PrayerName, val mode: AlertMode, val at: Instant)

    fun alerts(
        place: GeoPoint,
        zone: ZoneId,
        settings: PrayerSettings,
        alerts: Map<String, String>,
        now: Instant,
    ): Map<String, Instant> {
        val today = now.atZone(zone).toLocalDate()
        return (0 until DAYS_AHEAD).flatMap { offset ->
            val day = PrayerCalculator.compute(place, today.plusDays(offset), zone, settings)
            PrayerName.entries.mapNotNull { prayer ->
                val mode = AlertMode.resolve(prayer, alerts)
                val at = day[prayer]
                if (mode == AlertMode.OFF || !at.isAfter(now)) null else Alert(prayer, mode, at)
            }
        }.associate { key(it) to it.at }
    }

    fun key(alert: Alert) = listOf(
        PREFIX,
        alert.prayer.name,
        alert.mode.name,
        alert.at.toEpochMilli()
    ).joinToString(SEPARATOR)

    /** null for keys that are not ours or are malformed (e.g. written by a newer version). */
    fun parse(key: String): Alert? {
        val parts = key.split(SEPARATOR).takeIf { it.size == KEY_PARTS && it[INDEX_PREFIX] == PREFIX }
        val prayer = parts?.let { p -> PrayerName.entries.firstOrNull { it.name == p[INDEX_PRAYER] } }
        val mode = parts?.let { p -> AlertMode.entries.firstOrNull { it.name == p[INDEX_MODE] } }
        val at = parts?.get(INDEX_TIME)?.toLongOrNull()?.let(Instant::ofEpochMilli)
        return if (prayer != null && mode != null && at != null) Alert(prayer, mode, at) else null
    }

    private const val KEY_PARTS = 4
    private const val INDEX_PREFIX = 0
    private const val INDEX_PRAYER = 1
    private const val INDEX_MODE = 2
    private const val INDEX_TIME = 3
}
