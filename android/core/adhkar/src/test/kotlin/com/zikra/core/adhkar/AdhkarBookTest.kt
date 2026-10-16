package com.zikra.core.adhkar

import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** WP 2.14b: the bundled adhkar as the app reads them. */
@RunWith(RobolectricTestRunner::class)
class AdhkarBookTest {

    private val book = AdhkarBook.open(ApplicationProvider.getApplicationContext())

    @After
    fun close() = book.close()

    private val contentDir = File(requireNotNull(System.getProperty("zikra.contentDir")))

    @Test
    fun everyChapterAndDhikrIsThereWithItsReferences() {
        val chapters = book.chapters()
        assertEquals(AdhkarBook.CHAPTER_COUNT, chapters.size)
        assertEquals(AdhkarBook.DHIKR_COUNT, chapters.sumOf { it.size })
        val adhkar = chapters.flatMap { book.chapter(it.id) }
        assertEquals((1..AdhkarBook.DHIKR_COUNT).toList(), adhkar.map { it.id })
        assertTrue(adhkar.all { it.parts.isNotEmpty() && it.references.isNotEmpty() && it.repeat >= 1 })
    }

    @Test
    fun dailyChaptersAreTheOnesTheirNamesSay() {
        val titles = book.chapters().associate { it.id to it.title }
        assertTrue(titles.getValue(AdhkarBook.WAKING).contains("الاستيقاظ"))
        assertTrue(titles.getValue(AdhkarBook.AFTER_PRAYER).contains("بعد السلام"))
        assertTrue(titles.getValue(AdhkarBook.MORNING_EVENING).contains("الصباح والمساء"))
        assertTrue(titles.getValue(AdhkarBook.SLEEP).contains("النوم"))
    }

    @Test
    fun quotedVersesAreTheTanzilText() {
        val kursi = book.chapter(AdhkarBook.MORNING_EVENING).first { it.id == 75 }
        val verse = kursi.parts.filterIsInstance<DhikrPart.Quran>().single()
        assertEquals(2 to 255, verse.surah to verse.ayah)
        val tanzil = File(contentDir, "sources/tanzil/quran-uthmani.txt").readLines(Charsets.UTF_8)
            .first { it.startsWith("2|255|") }.substringAfter("2|255|")
        assertEquals(tanzil, verse.text)
        assertTrue(kursi.references.single().contains("سورة البقرة"))
    }

    @Test
    fun correctionsOfTheIndependentCheckAreShownNextToTheReferences() {
        val sleep = book.chapter(AdhkarBook.SLEEP).first { it.id == 106 }
        assertTrue(sleep.notes.single().contains("٢٧٢٧"))
        assertTrue(book.chapter(AdhkarBook.MORNING_EVENING).all { it.notes.isEmpty() })
    }

    @Test
    fun englishTitlesAndMeaningsWhereTheSourceHasThem() {
        val chapters = book.chapters("en")
        val waking0 = chapters.first { it.id == AdhkarBook.WAKING }
        assertEquals("supplications for when you wake up", waking0.translatedTitle)
        val waking = book.chapter(AdhkarBook.WAKING, "en").first()
        assertTrue(requireNotNull(waking.translation).meaning.contains("All praise is for Allah"))
        assertTrue(requireNotNull(waking.translation).transliteration.orEmpty().contains("Alhamdu"))
        // Left out where the source does not hold a sound translation (ADR-044).
        val doubt = book.chapters("en").first { c -> book.chapter(c.id).any { it.id == 133 } }
        assertEquals(null, book.chapter(doubt.id, "en").first { it.id == 133 }.translation)
        assertTrue(book.chapters().all { it.translatedTitle == null })
    }

    @Test
    fun sourcesAreKnownForDisplay() {
        assertTrue(book.info.textUrl.startsWith("https://"))
        assertTrue(book.info.referencesUrl.contains("shamela"))
        assertEquals("tanzil-quran-uthmani", book.info.quranSource)
    }
}
