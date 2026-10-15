package com.zikra.core.prayer

/**
 * Suggested calculation method and Asr madhab for a country, used until the user chooses (ADR-032).
 * PROVISIONAL: follows the Adhan library's guidance and the authorities each method is named after;
 * to be confirmed by the religious reviewer (ADR-P07). The screen always shows the method in use.
 */
object RegionalDefaults {

    private val methodByCountry: Map<String, CalculationMethodId> = buildMap {
        put("SA", CalculationMethodId.UMM_AL_QURA)
        put("EG", CalculationMethodId.EGYPTIAN)
        listOf("PK", "IN", "BD", "AF").forEach { put(it, CalculationMethodId.KARACHI) }
        put("AE", CalculationMethodId.DUBAI)
        put("KW", CalculationMethodId.KUWAIT)
        put("QA", CalculationMethodId.QATAR)
        // The Singapore parameters are the ones used in Singapore, Malaysia and Indonesia.
        listOf("SG", "MY", "ID", "BN").forEach { put(it, CalculationMethodId.SINGAPORE) }
        put("TR", CalculationMethodId.TURKEY)
        // Recommended by the Adhan library for North America and the UK.
        listOf("US", "CA", "GB").forEach { put(it, CalculationMethodId.MOONSIGHTING_COMMITTEE) }
    }

    /** Hanafi Asr is the prevailing practice in these countries; elsewhere the standard (majority) Asr. */
    private val hanafiCountries = setOf("PK", "IN", "BD", "AF")

    fun method(countryCode: String?): CalculationMethodId =
        methodByCountry[countryCode?.uppercase()] ?: CalculationMethodId.MUSLIM_WORLD_LEAGUE

    fun madhab(countryCode: String?): AsrMadhab =
        if (countryCode?.uppercase() in hanafiCountries) AsrMadhab.HANAFI else AsrMadhab.STANDARD
}
