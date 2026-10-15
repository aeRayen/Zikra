package com.zikra.core.places

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

/**
 * Runs against the real places.sqlite generated from the pinned GeoNames sources. Expected names are
 * compared with what the source data holds, never with names typed here, except for the ids
 * (GeoNames ids are stable).
 */
@RunWith(RobolectricTestRunner::class)
class PlaceDirectoryTest {

    // Robolectric gives every test a fresh environment, so the database is opened per test.
    private val dir = PlaceDirectory.open(ApplicationProvider.getApplicationContext())

    @After
    fun closeDirectory() = dir.close()

    @Test
    fun findsMakkahByEveryCommonName() {
        for (query in listOf("Mecca", "makkah", "MUKARRAMAH", "مكة", "مكه", "مَكَّة")) {
            assertEquals("query $query", MAKKAH, dir.search(query, "en").firstOrNull()?.cityId)
        }
    }

    @Test
    fun namesFollowTheRequestedLanguageWithFallback() {
        val ar = requireNotNull(dir.byId(CAIRO, "ar"))
        val en = requireNotNull(dir.byId(CAIRO, "en"))
        assertNotEquals(ar.name, en.name)
        assertTrue("Arabic name ${ar.name}", ar.name.all { it.code in ARABIC_BLOCK || it == ' ' })
        assertEquals("EG", en.countryCode)
        assertEquals(ZoneId.of("Africa/Cairo"), en.timeZone)
        // Language without names for this city falls back to English, never to an empty string.
        assertEquals(en.name, requireNotNull(dir.byId(CAIRO, "xx")).name)
        assertEquals(ar.name, requireNotNull(dir.byId(CAIRO, "ar-EG")).name)
    }

    @Test
    fun biggerCityRanksFirstForSharedPrefix() {
        val results = dir.search("lond", "en")
        assertEquals(LONDON, results.first().cityId)
        assertTrue(results.size > 1)
    }

    @Test
    fun emptyOrPunctuationOnlyQueryReturnsNothing() {
        assertTrue(dir.search("", "en").isEmpty())
        assertTrue(dir.search("   ", "en").isEmpty())
        assertTrue(dir.search("zzqxj", "en").isEmpty())
    }

    @Test
    fun nearestFindsTheCityForACoarseLocation() {
        // ~5 km from the Kaaba: still Makkah.
        val near = requireNotNull(dir.nearest(21.46, 39.85, "en"))
        assertEquals(MAKKAH, near.place.cityId)
        assertTrue(near.distanceKm < 10)
        // Coarse location in central London.
        assertEquals(LONDON, dir.nearest(51.51, -0.12, "en")?.place?.cityId)
    }

    @Test
    fun nearestIsNullFarFromAnyCity() {
        assertNull(dir.nearest(-60.0, -140.0, "en")) // Southern Ocean
    }

    @Test
    fun distanceFormulaIsSane() {
        // London -> Paris is about 344 km.
        assertEquals(344.0, PlaceDirectory.distanceKm(51.5074, -0.1278, 48.8566, 2.3522), 3.0)
    }

    companion object {
        private const val MAKKAH = 104515L
        private const val CAIRO = 360630L
        private const val LONDON = 2643743L
        private val ARABIC_BLOCK = 0x0600..0x06FF
    }
}
