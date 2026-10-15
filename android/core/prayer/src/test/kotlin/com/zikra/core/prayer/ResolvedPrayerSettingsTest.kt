package com.zikra.core.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedPrayerSettingsTest {

    private fun resolve(
        method: String? = null,
        madhab: String? = null,
        rule: String? = null,
        adjustments: Map<String, Int> = emptyMap(),
        country: String? = null,
    ) = ResolvedPrayerSettings.resolve(method, madhab, rule, adjustments, country)

    @Test
    fun automaticUsesTheRegionalDefault() {
        val sa = resolve(country = "SA")
        assertEquals(CalculationMethodId.UMM_AL_QURA, sa.settings.method)
        assertEquals(AsrMadhab.STANDARD, sa.settings.madhab)
        assertTrue(sa.methodIsAutomatic && sa.madhabIsAutomatic)
        assertEquals(AsrMadhab.HANAFI, resolve(country = "pk").settings.madhab) // case-insensitive
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, resolve(country = "FR").settings.method)
        assertEquals(CalculationMethodId.MUSLIM_WORLD_LEAGUE, resolve(country = null).settings.method)
    }

    @Test
    fun userChoiceWinsOverTheDefault() {
        val r = resolve(method = "egyptian", madhab = "HANAFI", rule = "TWILIGHT_ANGLE", country = "SA")
        assertEquals(CalculationMethodId.EGYPTIAN, r.settings.method)
        assertEquals(AsrMadhab.HANAFI, r.settings.madhab)
        assertEquals(HighLatitudeRule.TWILIGHT_ANGLE, r.settings.highLatitudeRule)
        assertFalse(r.methodIsAutomatic || r.madhabIsAutomatic)
    }

    @Test
    fun unknownOrInvalidValuesFallBackInsteadOfFailing() {
        val r = resolve(
            method = "tehran",
            madhab = "JAFARI",
            rule = "SOMETHING_NEW",
            adjustments = mapOf("FAJR" to 2, "ISHA" to 99, "TAHAJJUD" to 1),
            country = "EG",
        )
        assertEquals(CalculationMethodId.EGYPTIAN, r.settings.method)
        assertTrue(r.methodIsAutomatic)
        assertEquals(AsrMadhab.STANDARD, r.settings.madhab)
        assertNull(r.settings.highLatitudeRule)
        assertEquals(mapOf(PrayerName.FAJR to 2), r.settings.adjustmentMinutes)
    }

    @Test
    fun everyDefaultMethodIsAnOfferedMethod() {
        val countries =
            listOf("SA", "EG", "PK", "IN", "BD", "AF", "AE", "KW", "QA", "SG", "MY", "ID", "BN", "TR", "US", "CA", "GB")
        countries.forEach { assertTrue(it, RegionalDefaults.method(it) in CalculationMethodId.entries) }
    }
}
