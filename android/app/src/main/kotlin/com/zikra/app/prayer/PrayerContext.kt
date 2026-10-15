package com.zikra.app.prayer

import com.zikra.core.places.Place
import com.zikra.core.places.PlaceLookup
import com.zikra.core.prayer.GeoPoint
import com.zikra.core.prayer.ResolvedPrayerSettings
import com.zikra.core.settings.PrayerPreferences
import com.zikra.core.settings.SavedLocation
import java.time.ZoneId

/** Everything needed to compute prayer times for the saved location with the user's preferences. */
data class PrayerContext(
    val saved: SavedLocation,
    val place: Place?,
    val point: GeoPoint,
    val zone: ZoneId,
    val resolved: ResolvedPrayerSettings,
) {
    companion object {
        /** Blocking (city lookup): call on an IO dispatcher. An invalid stored zone falls back to [fallbackZone]. */
        fun of(
            saved: SavedLocation,
            prefs: PrayerPreferences,
            places: PlaceLookup,
            language: String,
            fallbackZone: ZoneId,
        ): PrayerContext {
            val place = saved.cityId?.let { places.byId(it, language) }
            return PrayerContext(
                saved = saved,
                place = place,
                point = GeoPoint(saved.latitude, saved.longitude),
                zone = runCatching { ZoneId.of(saved.timeZone) }.getOrElse { fallbackZone },
                resolved = ResolvedPrayerSettings.resolve(
                    prefs.methodId,
                    prefs.madhab,
                    prefs.highLatitudeRule,
                    prefs.adjustmentMinutes,
                    place?.countryCode,
                ),
            )
        }
    }
}
