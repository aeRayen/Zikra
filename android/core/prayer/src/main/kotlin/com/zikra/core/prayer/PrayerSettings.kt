package com.zikra.core.prayer

import com.batoulapps.adhan2.CalculationMethod

/** The five prayers plus sunrise (shown, never an adhan). Order = order of the day. */
enum class PrayerName { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/**
 * Supported calculation methods. The stable [id] is what we store and sync (settings.calculation_method).
 * Tehran is not offered: the bundled engine version cannot express its Maghrib angle.
 */
enum class CalculationMethodId(val id: String, internal val engine: CalculationMethod) {
    MUSLIM_WORLD_LEAGUE("mwl", CalculationMethod.MUSLIM_WORLD_LEAGUE),
    EGYPTIAN("egyptian", CalculationMethod.EGYPTIAN),
    KARACHI("karachi", CalculationMethod.KARACHI),
    UMM_AL_QURA("umm_al_qura", CalculationMethod.UMM_AL_QURA),
    DUBAI("dubai", CalculationMethod.DUBAI),
    MOONSIGHTING_COMMITTEE("moonsighting", CalculationMethod.MOON_SIGHTING_COMMITTEE),
    NORTH_AMERICA("isna", CalculationMethod.NORTH_AMERICA),
    KUWAIT("kuwait", CalculationMethod.KUWAIT),
    QATAR("qatar", CalculationMethod.QATAR),
    SINGAPORE("singapore", CalculationMethod.SINGAPORE),
    TURKEY("turkey", CalculationMethod.TURKEY),
    ;

    companion object {
        fun fromId(id: String?): CalculationMethodId? = entries.firstOrNull { it.id == id }
    }
}

/** Asr shadow length: standard (Shafi'i, Maliki, Hanbali) = 1, Hanafi = 2. */
enum class AsrMadhab { STANDARD, HANAFI }

/** How Fajr/Isha are bounded where twilight barely ends (high latitudes). */
enum class HighLatitudeRule { MIDDLE_OF_THE_NIGHT, SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE }

/**
 * User's prayer settings (PROJECT_REQUIREMENTS: method, madhab, per-prayer minute adjustments).
 * [highLatitudeRule] null = the engine's recommendation for the location.
 */
data class PrayerSettings(
    val method: CalculationMethodId,
    val madhab: AsrMadhab = AsrMadhab.STANDARD,
    val highLatitudeRule: HighLatitudeRule? = null,
    val adjustmentMinutes: Map<PrayerName, Int> = emptyMap(),
) {
    init {
        require(adjustmentMinutes.values.all { it in -MAX_ADJUSTMENT..MAX_ADJUSTMENT }) {
            "Adjustments must be within ±$MAX_ADJUSTMENT minutes"
        }
    }

    private companion object {
        const val MAX_ADJUSTMENT = 30
    }
}

/** A location in decimal degrees (WGS84). Only used on the device (ADR-018). */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -MAX_LATITUDE..MAX_LATITUDE) { "latitude out of range" }
        require(longitude in -MAX_LONGITUDE..MAX_LONGITUDE) { "longitude out of range" }
    }

    private companion object {
        const val MAX_LATITUDE = 90.0
        const val MAX_LONGITUDE = 180.0
    }
}
