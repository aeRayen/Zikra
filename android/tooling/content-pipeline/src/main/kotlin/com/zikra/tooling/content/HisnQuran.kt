package com.zikra.tooling.content

import com.zikra.core.i18n.SearchNormalizer

/** A piece of a dhikr as displayed: its own words, or one ayah (or part of one) in the Tanzil Uthmani text. */
sealed interface DhikrPart {
    data class Words(val text: String) : DhikrPart

    data class Quran(val surah: Int, val ayah: Int, val text: String) : DhikrPart
}

/**
 * Verses quoted inside a dhikr (between ﴿ and ﴾) are shown in the verified Tanzil Uthmani text, never
 * in the spelling of the adhkar source (WP 2.14b, ADR-041). Each quote is located in the Tanzil
 * simple-clean text (same words, modern spelling) by its letters alone, so spacing and the ways of
 * writing alef and hamza do not matter; the same word range is then taken from the Uthmani text.
 * A quote found at any number of places other than one fails the build, except a one-word mention of a
 * surah (e.g. «يقرأ ﴿الم﴾ تنزيل السجدة»), which is a name, not a recitation, and stays as written.
 */
class HisnQuran(simple: List<Ayah>, uthmani: List<Ayah>) {
    private class Word(val surah: Int, val ayah: Int, val index: Int)

    private val words = mutableListOf<Word>()

    /** Letters of the whole simple-clean text, and for each letter the index of its word in [words]. */
    private val letters = StringBuilder()
    private val owner = mutableListOf<Int>()
    private val simpleWordCount = simple.associate { (it.surah to it.ayah) to it.text.split(' ').size }
    private val uthmani = uthmani.associate { (it.surah to it.ayah) to it.text }

    init {
        simple.forEach { a ->
            a.text.split(' ').forEachIndexed { i, w ->
                val k = key(w)
                k.forEach { _ -> owner += words.size }
                letters.append(k)
                words += Word(a.surah, a.ayah, i)
            }
        }
    }

    fun parts(dhikrId: Int, text: String): List<DhikrPart> {
        val parts = mutableListOf<DhikrPart>()
        var last = 0
        for (m in quote.findAll(text)) {
            val span = locate(m.groupValues[1])
            val oneWord = m.groupValues[1].trim().split(separators).size == 1
            if (span == null && oneWord) continue // a surah's name, kept as written
            val found = span
                ?: throw PipelineException("Dhikr $dhikrId: quote ${m.range} is not found at exactly one place")
            text.substring(last, m.range.first).takeIf { it.isNotBlank() }?.let { parts += DhikrPart.Words(it.trim()) }
            parts += quranParts(dhikrId, found)
            last = m.range.last + 1
        }
        text.substring(last).takeIf { it.isNotBlank() }?.let { parts += DhikrPart.Words(it.trim()) }
        return parts
    }

    /** The words of the only place whose letters are exactly the quote's, from a word start to a word end. */
    private fun locate(quoted: String): List<Word>? {
        val k = key(quoted)
        if (k.isEmpty()) return null
        val found = mutableListOf<IntRange>()
        var at = letters.indexOf(k)
        while (at >= 0) {
            val end = at + k.length - 1
            val startsWord = at == 0 || owner[at - 1] != owner[at]
            val endsWord = end == owner.lastIndex || owner[end + 1] != owner[end]
            if (startsWord && endsWord) found += owner[at]..owner[end]
            at = letters.indexOf(k, at + 1)
        }
        return found.singleOrNull()?.let { words.subList(it.first, it.last + 1) }
    }

    /** One part per ayah; a partly quoted ayah takes the same word range from the Uthmani text. */
    private fun quranParts(dhikrId: Int, span: List<Word>): List<DhikrPart.Quran> =
        span.groupBy { it.surah to it.ayah }.map { (position, quoted) ->
            val full = uthmani.getValue(position)
            val count = simpleWordCount.getValue(position)
            val text = if (quoted.size == count) {
                full
            } else {
                val tokens = full.split(' ')
                val wordTokens = tokens.indices.filterNot { i -> tokens[i].all { it in pauseMarks } }
                ensure(wordTokens.size == count) {
                    "Dhikr $dhikrId: ${position.first}:${position.second} is quoted in part and its two Tanzil " +
                        "texts do not split into the same words"
                }
                tokens.subList(wordTokens[quoted.first().index], wordTokens[quoted.last().index] + 1).joinToString(" ")
            }
            DhikrPart.Quran(position.first, position.second, text)
        }

    private companion object {
        val quote = Regex("﴿([^﴾]*)﴾")
        val separators = Regex("[\\s*،,.؛:]+")

        /** Ayah pause marks of the Uthmani text (ۖ ۗ ۘ ۙ ۚ ۛ ۜ ۞ ۩): written as separate tokens. */
        val pauseMarks = setOf('ۖ', 'ۗ', 'ۘ', 'ۙ', 'ۚ', 'ۛ', 'ۜ', '۞', '۩')

        /** Alef and every hamza form: the two spellings write them differently (السموات / السماوات, يؤوده / يئوده). */
        val alefHamza = Regex("[اأإآٱءؤئ]")

        fun key(text: String): String = SearchNormalizer.normalize(text.replace(alefHamza, "")).filter { it.isLetter() }
    }
}
