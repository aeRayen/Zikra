package com.zikra.app.ui

import com.zikra.app.R
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName

/** Localized labels shared by the prayer screens and notifications. */
internal fun PrayerName.labelRes(): Int = when (this) {
    PrayerName.FAJR -> R.string.prayer_fajr
    PrayerName.SUNRISE -> R.string.prayer_sunrise
    PrayerName.DHUHR -> R.string.prayer_dhuhr
    PrayerName.ASR -> R.string.prayer_asr
    PrayerName.MAGHRIB -> R.string.prayer_maghrib
    PrayerName.ISHA -> R.string.prayer_isha
}

internal fun CalculationMethodId.labelRes(): Int = when (this) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> R.string.method_mwl
    CalculationMethodId.EGYPTIAN -> R.string.method_egyptian
    CalculationMethodId.KARACHI -> R.string.method_karachi
    CalculationMethodId.UMM_AL_QURA -> R.string.method_umm_al_qura
    CalculationMethodId.DUBAI -> R.string.method_dubai
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> R.string.method_moonsighting
    CalculationMethodId.NORTH_AMERICA -> R.string.method_isna
    CalculationMethodId.KUWAIT -> R.string.method_kuwait
    CalculationMethodId.QATAR -> R.string.method_qatar
    CalculationMethodId.SINGAPORE -> R.string.method_singapore
    CalculationMethodId.TURKEY -> R.string.method_turkey
}
