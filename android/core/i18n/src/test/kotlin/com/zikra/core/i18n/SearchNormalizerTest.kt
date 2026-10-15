package com.zikra.core.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fixtures are ordinary Arabic/Persian/Latin words, deliberately not Quranic text
 * (CONTRIBUTING §5: no religious text typed by hand in code).
 */
class SearchNormalizerTest {

    private fun assertNormalizes(expected: String, input: String) =
        assertEquals("normalize(\"$input\")", expected, SearchNormalizer.normalize(input))

    @Test
    fun removesTashkeel() {
        assertNormalizes("مدرسه", "مَدْرَسَةٌ")
        assertNormalizes("كتب", "كَتَبَ")
    }

    @Test
    fun unifiesAlefForms() {
        assertNormalizes("احمد", "أحمد")
        assertNormalizes("اسلام", "إسلام")
        assertNormalizes("امال", "آمال")
        assertNormalizes("الوصل", "ٱلوصل")
    }

    @Test
    fun foldsHamzaCarriersYehAndTaMarbuta() {
        assertNormalizes("مومن", "مؤمن")
        assertNormalizes("قايم", "قائم")
        assertNormalizes("مستشفي", "مستشفى")
        assertNormalizes("فاطمه", "فاطمة")
    }

    @Test
    fun removesTatweelAndSuperscriptAlef() {
        assertNormalizes("محمد", "مـحـمـد")
        assertNormalizes("هذا", "هٰذا")
    }

    @Test
    fun foldsPresentationFormsAndLigatures() {
        assertNormalizes("لا", "ﻻ") // ﻻ lam-alef ligature
    }

    @Test
    fun foldsPersianAndUrduLetters() {
        assertNormalizes("كتاب يك", "کتاب یک")
    }

    @Test
    fun convertsAllDigitSystemsToLatin() {
        assertNormalizes("123 45", "١٢٣ ۴۵")
    }

    @Test
    fun foldsLatinCaseAndAccents() {
        assertNormalizes("cafe deja vu", "Café  Déjà\tVu")
    }

    @Test
    fun queryAndIndexedTextMatchAfterNormalization() {
        val indexed = SearchNormalizer.normalize("إِسْتِغْفَارٌ")
        val query = SearchNormalizer.normalize("استغفار")
        assertEquals(indexed, query)
    }

    @Test
    fun isIdempotent() {
        val once = SearchNormalizer.normalize("أَحْمَدُ مؤمنٌ ١٢")
        assertEquals(once, SearchNormalizer.normalize(once))
    }
}
