package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** ADR-044: the English meaning and transliteration of each dhikr, attached only where it belongs. */
class HisnTranslationTest {
    private val sources = File(requireNotNull(System.getProperty("zikra.contentDir")), "sources")
    private val arabic by lazy { parseHisnMuslim(File(sources, "hisnmuslim/ar/")) }
    private val english by lazy { parseHisnMuslimTranslation(File(sources, "hisnmuslim/en/")) }

    @Test
    fun everyTranslationBelongsToItsDhikrExceptTheTwoTheSourceGotWrong() {
        assertEquals(HisnReferences.DHIKR_COUNT, english.size)
        // 133: a duplicated key puts the meaning where the transliteration goes; 185: no meaning at all.
        assertEquals(setOf(133, 185), HisnTranslations.problems(arabic, english).keys)
    }

    @Test
    fun wellKnownAdhkarHaveTheirMeaning() {
        val waking = english.first { it.id == 1 }
        assertTrue(waking.meaning.contains("All praise is for Allah who gave us life"))
        assertTrue(waking.transliteration.contains("Alhamdu lillah"))
        assertEquals("supplications for when you wake up", waking.chapterTitle)
    }

    @Test
    fun aMissingClosingQuoteInATitleIsTheOnlyRepair() {
        // The colon is the JSON separator; only the closing quote before it was missing.
        assertEquals("{\n\"Title\":\n[", repairTitle("{\n\"Title:\n["))
        assertEquals("{\"a\": 1}", repairTitle("{\"a\": 1}"))
    }
}
