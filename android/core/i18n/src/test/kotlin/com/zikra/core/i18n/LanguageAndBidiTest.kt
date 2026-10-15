package com.zikra.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LanguageAndBidiTest {

    @Test
    fun resolvesExactBaseAndFallback() {
        assertEquals(SupportedLanguages.ARABIC, SupportedLanguages.resolve("ar"))
        assertEquals(SupportedLanguages.ARABIC, SupportedLanguages.resolve("ar-EG"))
        assertEquals(SupportedLanguages.ENGLISH, SupportedLanguages.resolve("en-GB"))
        assertEquals(SupportedLanguages.fallback, SupportedLanguages.resolve("sw"))
        assertEquals(SupportedLanguages.fallback, SupportedLanguages.resolve(null))
    }

    @Test
    fun knowsDirectionOfLanguagesNotYetEnabled() {
        assertEquals(TextDirection.RTL, SupportedLanguages.directionOf("ur-PK"))
        assertEquals(TextDirection.RTL, SupportedLanguages.directionOf("fa"))
        assertEquals(TextDirection.LTR, SupportedLanguages.directionOf("tr"))
    }

    @Test
    fun everySupportedLanguageIsValidAndUnique() {
        val tags = SupportedLanguages.all.map { it.tag }
        assertEquals(tags.size, tags.toSet().size)
        SupportedLanguages.all.forEach { assertEquals(SupportedLanguages.directionOf(it.tag), it.direction) }
    }

    @Test
    fun rejectsInvalidTags() {
        assertThrows(IllegalArgumentException::class.java) { Language("Arabic", "x", TextDirection.RTL) }
    }

    @Test
    fun isolatesAndStrips() {
        val isolated = Bidi.isolate("الفاتحة")
        assertEquals('⁨', isolated.first())
        assertEquals('⁩', isolated.last())
        assertEquals("Surah الفاتحة", Bidi.stripIsolates("Surah $isolated"))
    }
}
