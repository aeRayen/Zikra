package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

class HisnQuranTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sources = File(requireNotNull(System.getProperty("zikra.contentDir")), "sources")
    private val book by lazy { HisnPipeline(sources).adhkarBook() }
    private val uthmani by lazy {
        parseTanzilText(File(sources, "tanzil/quran-uthmani.txt")).ayahs.associate { (it.surah to it.ayah) to it.text }
    }

    private fun quranOf(id: Int) = book.parts.getValue(id).filterIsInstance<DhikrPart.Quran>()

    @Test
    fun everyQuotedVerseIsShownFromTheTanzilText() {
        val withVerses = book.parts.filterValues { p -> p.any { it is DhikrPart.Quran } }.keys
        assertEquals(
            setOf(4, 50, 70, 71, 74, 75, 76, 99, 100, 101, 135, 206, 207, 235, 236),
            withVerses,
        )
        book.parts.values.flatten().filterIsInstance<DhikrPart.Quran>().forEach { part ->
            val ayah = uthmani.getValue(part.surah to part.ayah)
            assertTrue("${part.surah}:${part.ayah}", ayah.contains(part.text))
        }
        // No quote is left in the adhkar source's own spelling, except a surah's name.
        val leftQuotes = book.parts.mapValues { (_, p) ->
            p.filterIsInstance<DhikrPart.Words>().filter { it.text.contains('﴿') }
        }.filterValues { it.isNotEmpty() }.keys
        assertEquals(setOf(110), leftQuotes)
    }

    @Test
    fun wholeAndPartlyQuotedAyahsLandOnTheRightPlace() {
        assertEquals((190..200).toList(), quranOf(4).map { it.ayah })
        assertEquals(listOf(2 to 255), quranOf(75).map { it.surah to it.ayah })
        assertEquals(uthmani.getValue(2 to 255), quranOf(75).single().text)
        // Partly quoted: the end of al-Mu'minun 14, the end of az-Zukhruf 13.
        // (Compared with the pinned Tanzil text itself: Quran text is never typed in code.)
        assertEquals(lastWords(uthmani.getValue(23 to 14), 4), quranOf(50).single().text)
        assertEquals(lastWords(uthmani.getValue(43 to 13), 9), quranOf(206).first().text)
        // Al-Ikhlas without the basmala Tanzil writes at the head of its first ayah (said before it, as written).
        assertEquals(lastWords(uthmani.getValue(112 to 1), 4), quranOf(70).first().text)
    }

    private fun lastWords(text: String, n: Int) = text.split(' ').takeLast(n).joinToString(" ")

    @Test
    fun partlyQuotedWordCountsMatch() {
        assertEquals(4, quranOf(50).single().text.split(' ').size)
    }

    @Test
    fun theDhikrsOwnWordsAreKeptVerbatim() {
        book.adhkar.forEach { d ->
            val shown = book.parts.getValue(d.id).joinToString("") {
                when (it) {
                    is DhikrPart.Words -> it.text
                    is DhikrPart.Quran -> ""
                }
            }
            val expected = d.text.replace(Regex("﴿[^﴾]*﴾")) { m -> if (d.id == 110) m.value else "" }
            fun letters(s: String) = s.filterNot { it.isWhitespace() }
            assertEquals("dhikr ${d.id}", letters(expected), letters(shown))
        }
    }

    @Test
    fun aQuoteFoundAtSeveralPlacesFailsTheBuild() {
        // Neutral placeholder words: the locator does not depend on the text (no Quran text in code).
        val twice = "كلمة أولى كلمة ثانية"
        val ayahs = listOf(Ayah(1, 1, twice), Ayah(2, 1, twice))
        val quran = HisnQuran(simple = ayahs, uthmani = ayahs)
        assertThrows(PipelineException::class.java) { quran.parts(1, "﴿كلمة أولى﴾") }
        assertThrows(PipelineException::class.java) { quran.parts(1, "﴿كلمة ثالثة رابعة﴾") }
        // Found once: taken from the second text.
        val once = HisnQuran(
            simple = listOf(Ayah(1, 1, "الف باء تاء")),
            uthmani = listOf(Ayah(1, 1, "ألف باء تاء")),
        )
        assertEquals(listOf(DhikrPart.Quran(1, 1, "ألف باء تاء")), once.parts(1, "﴿الف باء تاء﴾"))
    }

    @Test
    fun theBundleHoldsEveryDhikrWithItsReferences() {
        val out = File(tmp.root, "adhkar.sqlite")
        HisnPipeline(sources).buildAdhkarBundle(out)
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { db ->
            fun count(sql: String) = db.createStatement().use { st ->
                st.executeQuery(sql).use {
                    it.next()
                    it.getInt(1)
                }
            }
            assertEquals(HisnReferences.DHIKR_COUNT, count("SELECT COUNT(*) FROM dhikr"))
            assertEquals(HisnReferences.CHAPTER_COUNT, count("SELECT COUNT(*) FROM adhkar_chapter"))
            assertEquals(0, count("SELECT COUNT(*) FROM dhikr WHERE id NOT IN (SELECT dhikr FROM dhikr_reference)"))
            assertEquals(0, count("SELECT COUNT(*) FROM dhikr WHERE id NOT IN (SELECT dhikr FROM dhikr_part)"))
        }
    }
}
