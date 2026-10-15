package com.zikra.core.i18n

import java.text.Normalizer

/**
 * Builds the SEARCH copy of a text (I18N_SPEC_AR.md §10). The result is stored in a separate
 * search column / FTS table and is never displayed: displayed religious text is always the
 * verbatim source text (CONTENT_PACKS_AR.md §4).
 *
 * The same function must be applied to the indexed text and to the user's query.
 */
object SearchNormalizer {
    private const val TATWEEL = 'ـ'
    private val quranicAnnotations = 'ۖ'..'ۭ'
    private val combiningMarks = Regex("\\p{Mn}+")
    private val whitespace = Regex("\\s+")

    /** Letter folds applied after decomposition (only letters with no canonical decomposition). */
    private val letterFolds = mapOf(
        'ٱ' to 'ا', // ٱ alef wasla  -> ا
        'ى' to 'ي', // ى alef maksura -> ي
        'ة' to 'ه', // ة ta marbuta   -> ه
        'ی' to 'ي', // ی Farsi yeh    -> ي
        'ے' to 'ي', // ے Urdu yeh barree -> ي
        'ک' to 'ك', // ک keheh        -> ك
        'ہ' to 'ه', // ہ heh goal     -> ه
        'ھ' to 'ه', // ھ heh doachashmee -> ه
    )

    fun normalize(text: String): String {
        // NFKC first: folds presentation forms and ligatures (e.g. ﻻ -> لا) to base letters.
        val composed = Normalizer.normalize(text, Normalizer.Form.NFKC)
        // NFD then drop combining marks: removes tashkeel, hamza/madda above or below
        // (أ إ آ -> ا, ؤ -> و, ئ -> ي), the superscript alef, and Latin accents (é -> e).
        val stripped = Normalizer.normalize(composed, Normalizer.Form.NFD).replace(combiningMarks, "")
        val folded = buildString(stripped.length) {
            for (c in stripped) {
                if (c == TATWEEL || c in quranicAnnotations) continue
                append(letterFolds[c] ?: c)
            }
        }
        return NumeralSystem.toLatin(folded)
            .lowercase()
            .replace(whitespace, " ")
            .trim()
    }
}
