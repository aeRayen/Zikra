package com.zikra.core.quran

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * WP 2.9 acceptance on the app side: the text the app will DISPLAY (read through [QuranText] from the
 * bundled asset) is identical, character by character, to the pinned Tanzil source file.
 */
@RunWith(RobolectricTestRunner::class)
class QuranTextTest {

    private val quran = QuranText.open(ApplicationProvider.getApplicationContext())

    @After
    fun close() = quran.close()

    /** "surah|ayah|text" lines of the pinned source; comment lines (licence) are skipped. */
    private fun sourceLines(): Map<Pair<Int, Int>, String> {
        val dir = requireNotNull(System.getProperty("zikra.contentDir")) { "zikra.contentDir not set" }
        return File(dir, "sources/tanzil/quran-uthmani.txt").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .associate { line ->
                val (s, a, text) = line.split('|', limit = 3)
                (s.toInt() to a.toInt()) to text
            }
    }

    @Test
    fun everyDisplayedAyahIsIdenticalToTheSource() {
        val source = sourceLines()
        assertEquals(QuranText.AYAH_COUNT, source.size)
        var compared = 0
        for (surah in quran.surahs()) {
            for (ayah in quran.ayahs(surah.number)) {
                assertEquals("${ayah.surah}:${ayah.ayah}", source[ayah.surah to ayah.ayah], ayah.text)
                compared++
            }
        }
        assertEquals(QuranText.AYAH_COUNT, compared)
    }

    @Test
    fun structureIsComplete() {
        val surahs = quran.surahs()
        assertEquals(QuranText.SURAH_COUNT, surahs.size)
        assertEquals((1..114).toList(), surahs.map { it.number })
        assertEquals(QuranText.AYAH_COUNT, surahs.sumOf { it.ayahCount })
        assertEquals(7, quran.surah(1)?.ayahCount)
        assertEquals(286, quran.surah(2)?.ayahCount)
        assertNull(quran.surah(115))
    }

    @Test
    fun pagesAndJuzCoverEveryAyahExactlyOnce() {
        assertEquals(QuranText.AYAH_COUNT, (1..QuranText.PAGE_COUNT).sumOf { quran.page(it).size })
        assertEquals(QuranText.AYAH_COUNT, (1..QuranText.JUZ_COUNT).sumOf { quran.juz(it).size })
        assertEquals(1 to 1, quran.page(1).first().let { it.surah to it.ayah })
        assertEquals(2 to 142, quran.juz(2).first().let { it.surah to it.ayah }) // juz 2 starts at al-Baqarah 142
    }

    @Test
    fun fifteenSajdaVerses() {
        val sajdas = quran.surahs().flatMap { quran.ayahs(it.number) }.filter { it.sajda != null }
        assertEquals(15, sajdas.size)
    }

    @Test
    fun tanzilNoticeAndVersionTravelWithTheText() {
        assertTrue(quran.licenseNotice().contains("Tanzil", ignoreCase = true))
        assertTrue(quran.sourceVersion().isNotBlank())
    }
}
