package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HisnReferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val sources = File(requireNotNull(System.getProperty("zikra.contentDir")), "sources")
    private val references by lazy { HisnPipeline(sources).hisnReferences().associateBy { it.id } }

    @Test
    fun everyDhikrHasAVerifiedReference() {
        assertEquals(HisnReferences.DHIKR_COUNT, references.size)
        val notVerified = references.values.filter { it.status != ReferenceStatus.VERIFIED.name }
        assertTrue(notVerified.joinToString { "${it.id}:${it.status}" }, notVerified.isEmpty())
    }

    @Test
    fun wellKnownReferencesLandOnTheRightDhikr() {
        // Waking up: al-Bukhari 6314, Muslim 2711.
        assertTrue(references.getValue(1).footnotes.joinToString().contains("٦٣١٤"))
        // Ayat al-Kursi in the morning adhkar.
        assertTrue(references.getValue(75).footnotes.joinToString().contains("سورة البقرة، الآية: ٢٥٥"))
        // Sayyid al-Istighfar: al-Bukhari 6306.
        assertTrue(references.getValue(79).footnotes.joinToString().contains("٦٣٠٦"))
        // The intro's hadith (Abu Dawud 4855) must NOT be attached to the first dhikr.
        assertTrue(references.getValue(1).footnotes.none { it.contains("٤٨٥٥") })
    }

    private fun page(n: Int, paragraphs: List<String>, footnotes: List<String>) = ShamelaPage(n, paragraphs, footnotes)

    private fun dhikr(id: Int, chapter: Int, text: String) = HisnDhikr(id, chapter, "باب الاختبار", text, 1)

    @Test
    fun chapterOrderNumberIsNotTakenForAFootnoteMarker() {
        val pages = listOf(
            page(
                1,
                listOf("١ - باب الاختبار", "١ - (١) ((سبحان الله وبحمده)) (٢)."),
                listOf("(١) حاشية لا تخص الذكر.", "(٢) البخاري، برقم ١."),
            ),
        )
        val ref = HisnReferences.extract(pages, listOf(dhikr(1, 1, "( سبحان الله وبحمده )"))).single()
        assertEquals(listOf("(٢) البخاري، برقم ١."), ref.footnotes)
        assertEquals(ReferenceStatus.VERIFIED.name, ref.status)
    }

    @Test
    fun aFootnoteContinuedOnTheNextPageIsKeptWhole() {
        val pages = listOf(
            page(
                1,
                listOf("١ - باب الاختبار", "١ - (١) ((سبحان الله وبحمده)) (١)."),
                listOf("(١) أخرجه مسلم، برقم ٢٦٩٢، =")
            ),
            page(2, listOf("كلام بعد الذكر"), listOf("وانظر: صحيح الجامع.")),
        )
        val ref = HisnReferences.extract(pages, listOf(dhikr(1, 1, "( سبحان الله وبحمده )"))).single()
        assertEquals(listOf("(١) أخرجه مسلم، برقم ٢٦٩٢، = وانظر: صحيح الجامع."), ref.footnotes)
    }

    @Test
    fun differentTextOrMissingFootnoteIsNeverVerified() {
        val pages = listOf(
            page(
                1,
                listOf(
                    "١ - باب الاختبار",
                    "١ - (١) ((سبحان الله وبحمده)) (١).",
                    "٢ - (٢) ((لا حول ولا قوة إلا بالله))."
                ),
                listOf("(١) أخرجه مسلم.")
            ),
        )
        val refs = HisnReferences.extract(
            pages,
            listOf(dhikr(1, 1, "( الحمد لله رب العالمين حمدا كثيرا )"), dhikr(2, 1, "( لا حول ولا قوة إلا بالله )"))
        )
        assertEquals(ReferenceStatus.TEXT_DIFFERS.name, refs[0].status)
        assertEquals(ReferenceStatus.NO_REFERENCE.name, refs[1].status)
    }

    @Test
    fun changedSourceFolderIsRejected() {
        val copy = tmp.newFolder("sources")
        sources.copyRecursively(copy)
        File(copy, "hisnmuslim/ar/1.json").appendText(" ")
        assertThrows(PipelineException::class.java) { HisnPipeline(copy).hisnReferences() }
    }

    @Test
    fun shamelaTextRoundTrips() {
        val pages = listOf(page(7, listOf("فقرة"), listOf("(١) حاشية")))
        val file = File(tmp.root, "t.txt")
        HisnShamela.write(pages, file)
        assertEquals(pages, HisnShamela.read(file))
    }
}
