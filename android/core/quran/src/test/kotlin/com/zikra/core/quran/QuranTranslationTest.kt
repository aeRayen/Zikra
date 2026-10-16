package com.zikra.core.quran

import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** WP 2.13a: the bundled translation as the app shows it equals the pinned QuranEnc file, unmodified. */
@RunWith(RobolectricTestRunner::class)
class QuranTranslationTest {

    private val translation = QuranTranslation.openEnglish(ApplicationProvider.getApplicationContext())

    @After
    fun close() = translation.close()

    private fun source(): JSONObject {
        val dir = requireNotNull(System.getProperty("zikra.contentDir"))
        return JSONObject(File(dir, "sources/quranenc/english_saheeh_v1.1.2.json").readText(Charsets.UTF_8))
    }

    @Test
    fun everyAyahIsIdenticalToTheSourceIncludingFootnotes() {
        val ayahs = source().getJSONArray("ayahs")
        assertEquals(QuranText.AYAH_COUNT, ayahs.length())
        val bySurah = (1..QuranText.SURAH_COUNT).associateWith { translation.surah(it) }
        for (i in 0 until ayahs.length()) {
            val a = ayahs.getJSONObject(i)
            val shown = requireNotNull(bySurah.getValue(a.getInt("sura"))[a.getInt("aya")])
            assertEquals("${a.getInt("sura")}:${a.getInt("aya")}", a.getString("translation"), shown.text)
            val footnotes = a.optString("footnotes", "").ifBlank { null }
            assertEquals(footnotes, shown.footnotes)
        }
    }

    @Test
    fun sourceAndTermsAreKnownForDisplay() {
        val info = translation.info
        assertEquals("en", info.language)
        assertEquals("Saheeh International", info.title)
        assertEquals("1.1.2", info.version)
        assertTrue(info.publisher.contains("QuranEnc"))
        assertTrue(info.license.contains("no modification", ignoreCase = true))
        assertTrue(info.sourceUrl.startsWith("https://quranenc.com"))
    }

    @Test
    fun searchIgnoresCaseAndFindsInMushafOrder() {
        val results = translation.search("MERCIFUL")
        assertTrue(results.size > 10)
        assertEquals(1 to 1, results.first().let { it.surah to it.ayah })
        assertTrue(translation.search("m").isEmpty())
        assertTrue(translation.search("%%").isEmpty())
    }
}
