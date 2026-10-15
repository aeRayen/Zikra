package com.zikra.app.ui

import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.HighLatitudeRule
import com.zikra.core.prayer.PrayerName

/** Callbacks of [PrayerSettingsScreen]; null arguments mean «automatic». */
class PrayerSettingsActions(
    val onBack: () -> Unit,
    val onMethod: (CalculationMethodId?) -> Unit,
    val onMadhab: (AsrMadhab?) -> Unit,
    val onHighLatitudeRule: (HighLatitudeRule?) -> Unit,
    val onAdjust: (PrayerName, Int) -> Unit,
    val onReset: () -> Unit,
)

internal const val RESET_TAG = "prayer_settings_reset"
internal fun methodTag(method: CalculationMethodId?) = "method_${method?.id ?: "auto"}"
internal fun madhabTag(madhab: AsrMadhab?) = "madhab_${madhab?.name ?: "auto"}"
internal fun adjustTag(prayer: PrayerName, delta: Int) = "adjust_${prayer.name}_${if (delta < 0) "minus" else "plus"}"
internal fun adjustValueTag(prayer: PrayerName) = "adjust_${prayer.name}_value"
