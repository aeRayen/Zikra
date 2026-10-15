package com.zikra.tooling.content

/**
 * Structural checks for Hafs 'an 'Asim (Kufan count). The reference table is independent of the
 * downloaded files, so a corrupted or wrong-riwaya source cannot validate itself.
 */
object QuranValidation {
    const val SURAH_COUNT = 114
    const val AYAH_COUNT = 6236
    const val JUZ_COUNT = 30
    const val QUARTER_COUNT = 240
    const val PAGE_COUNT = 604
    const val SAJDA_COUNT = 15

    val AYAHS_PER_SURAH: List<Int> = listOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )

    /** Ayah text or translation keyed by position: complete, ordered, non-empty, Kufan counts. */
    fun checkAyahSequence(positions: List<Pair<Int, Int>>, texts: List<String>, what: String) {
        if (positions.size != AYAH_COUNT) fail("$what: ${positions.size} ayahs, expected $AYAH_COUNT")
        var index = 0
        AYAHS_PER_SURAH.forEachIndexed { s, count ->
            for (a in 1..count) {
                val (surah, ayah) = positions[index]
                if (surah != s + 1 || ayah != a) fail("$what: expected ${s + 1}:$a at row $index, found $surah:$ayah")
                if (texts[index].isBlank()) fail("$what: empty text at $surah:$ayah")
                index++
            }
        }
    }

    fun checkMetadata(meta: QuranMetadata) {
        if (meta.surahs.size != SURAH_COUNT) fail("metadata: ${meta.surahs.size} surahs")
        meta.surahs.forEachIndexed { i, s ->
            if (s.number != i + 1 || s.ayahCount != AYAHS_PER_SURAH[i]) {
                fail("metadata: surah ${s.number} has ${s.ayahCount} ayahs, expected ${AYAHS_PER_SURAH[i]}")
            }
            if (s.nameArabic.isBlank()) fail("metadata: surah ${s.number} has no Arabic name")
        }
        if (meta.juzStarts.size != JUZ_COUNT) fail("metadata: ${meta.juzStarts.size} juz")
        if (meta.quarterStarts.size != QUARTER_COUNT) fail("metadata: ${meta.quarterStarts.size} hizb quarters")
        if (meta.pageStarts.size != PAGE_COUNT) fail("metadata: ${meta.pageStarts.size} pages")
        if (meta.sajdas.size != SAJDA_COUNT) fail("metadata: ${meta.sajdas.size} sajdas")
    }

    /** SHA-256 of "7,286,...,6": recorded in the manifest so clients can re-check the structure. */
    fun perSurahCountsSha256(): String = sha256Hex(AYAHS_PER_SURAH.joinToString(",").toByteArray())

    private fun fail(message: String): Nothing = throw PipelineException(message)
}
