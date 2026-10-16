package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font
import java.io.File

/**
 * The bundled mushaf font (Amiri Quran 1.003, SIL OFL 1.1) must have a glyph for EVERY code point of
 * the Tanzil Uthmani text: a missing small high letter or pause mark would silently fall back to
 * another font or a box, i.e. show the Quran wrongly.
 */
class QuranFontCoverageTest {

    private val contentDir = File(requireNotNull(System.getProperty("zikra.contentDir")))
    private val fontFile = File(contentDir, "../android/core/quran/src/main/res/font/amiri_quran.ttf")

    @Test
    fun fontCoversEveryCodePointOfTheText() {
        val text = File(contentDir, "sources/tanzil/quran-uthmani.txt").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .joinToString("") { it.split('|', limit = 3)[2] }
        val font = Font.createFont(Font.TRUETYPE_FONT, fontFile)
        val codePoints = text.codePoints().toArray().toSet()
        val missing = codePoints.filter { !font.canDisplay(it) && !Character.isWhitespace(it) }
            .map { "U+%04X".format(it) }
        assertTrue("checked ${codePoints.size} distinct code points", codePoints.size > 40)
        assertEquals("Code points without a glyph", emptyList<String>(), missing)
    }

    @Test
    fun fontFileIsThePinnedRelease() {
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(fontFile.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals("e2a47644762d16bdfb6d33e0d8db8c6ff30beae84150ef5a705316bbd829455c", sha)
    }
}
