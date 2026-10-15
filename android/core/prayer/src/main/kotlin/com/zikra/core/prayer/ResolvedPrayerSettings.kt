package com.zikra.core.prayer

/** Settings actually used, and whether each came from the user or from [RegionalDefaults]. */
data class ResolvedPrayerSettings(
    val settings: PrayerSettings,
    val methodIsAutomatic: Boolean,
    val madhabIsAutomatic: Boolean,
) {
    companion object {
        /**
         * Builds the settings from stored values. Anything unknown or out of range (newer app version,
         * damaged file) falls back to automatic instead of failing: prayer times must always be shown.
         */
        fun resolve(
            methodId: String?,
            madhab: String?,
            highLatitudeRule: String?,
            adjustmentMinutes: Map<String, Int>,
            countryCode: String?,
        ): ResolvedPrayerSettings {
            val method = CalculationMethodId.fromId(methodId)
            val chosenMadhab = AsrMadhab.entries.firstOrNull { it.name == madhab }
            val adjustments = adjustmentMinutes.mapNotNull { (name, minutes) ->
                val prayer = PrayerName.entries.firstOrNull { it.name == name }
                if (prayer == null || minutes !in -PrayerSettings.MAX_ADJUSTMENT..PrayerSettings.MAX_ADJUSTMENT) {
                    null
                } else {
                    prayer to minutes
                }
            }.toMap()
            return ResolvedPrayerSettings(
                settings = PrayerSettings(
                    method = method ?: RegionalDefaults.method(countryCode),
                    madhab = chosenMadhab ?: RegionalDefaults.madhab(countryCode),
                    highLatitudeRule = HighLatitudeRule.entries.firstOrNull { it.name == highLatitudeRule },
                    adjustmentMinutes = adjustments,
                ),
                methodIsAutomatic = method == null,
                madhabIsAutomatic = chosenMadhab == null,
            )
        }
    }
}
