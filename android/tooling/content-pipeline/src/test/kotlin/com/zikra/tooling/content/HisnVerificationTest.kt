package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

/** ADR-042: the references checked against an independent copy of the six books. */
class HisnVerificationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sources = File(requireNotNull(System.getProperty("zikra.contentDir")), "sources")
    private val verified by lazy { HisnPipeline(sources).verifyReferences() }

    private fun parse(footnote: String) = HisnCitations.parse(footnote)

    @Test
    fun citationsAreReadAsTheAuthorWritesThem() {
        assertEquals(
            listOf(Citation(HadithBook.BUKHARI, 6314), Citation(HadithBook.MUSLIM, 2711)),
            parse("البخاري مع الفتح، ١١/ ١١٣، برقم ٦٣١٤، ومسلم، ٤/ ٢٠٨٣، برقم ٢٧١١."),
        )
        // «برقم:» «برقم،» and «رقم» without the ba'.
        assertEquals(listOf(Citation(HadithBook.BUKHARI, 2893)), parse("البخاري، ٧/ ١٥٨، برقم: ٢٨٩٣"))
        assertEquals(listOf(Citation(HadithBook.ABU_DAWUD, 1522)), parse("أبو داود، ٢/ ٨٦، برقم، ١٥٢٢"))
        assertEquals(listOf(Citation(HadithBook.MUSLIM, 591)), parse("مسلم، ١/ ٤١٤، رقم ٥٩١."))
    }

    @Test
    fun numbersOfOtherWorksAreNotTakenForTheSixBooks() {
        // Another work by the same author, al-Albani's books, a dhikr number of this book, a page.
        assertTrue(parse("النسائي في عمل اليوم والليلة، برقم ١٠٠").isEmpty())
        assertTrue(parse("الترمذي، وانظر: صحيح الترمذي، برقم ٣٤٤٦").isEmpty())
        assertTrue(parse("البخاري، برقم: ٢٨٩٣، وسيأتي ص ٨٩، برقم ١٣٧.").none { it.number == 137 })
        assertTrue(parse("أبو داود، برقم، وتقدم تخريجه برقم ٣١").isEmpty())
    }

    @Test
    fun everyReferenceIsConfirmedOrReviewed() {
        assertTrue(verified.open.joinToString(), verified.open.isEmpty())
        assertTrue(verified.stale.joinToString(), verified.stale.isEmpty())
        // Waking up: al-Bukhari 6314 holds the dhikr's words.
        val waking = verified.results.first { it.dhikr.id == 1 }
        assertEquals(CitationCheck.MATCH, waking.citations.first { it.citation.book == HadithBook.BUKHARI }.result)
    }

    @Test
    fun wrongNumbersInTheBookBecomeVisibleCorrections() {
        val corrections = verified.review.corrections()
        assertEquals(setOf(33, 41, 59, 106, 109, 185, 188, 263), corrections.keys)
        assertTrue(corrections.getValue(106).single().contains("٢٧٢٧"))
        val out = File(tmp.root, "adhkar.sqlite")
        HisnPipeline(sources).buildAdhkarBundle(out)
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { db ->
            db.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM dhikr_note").use {
                    it.next()
                    assertEquals(corrections.values.sumOf { list -> list.size }, it.getInt(1))
                }
            }
        }
    }
}
