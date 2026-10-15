package com.zikra.core.i18n

/**
 * Unicode bidirectional isolation (I18N_SPEC_AR.md §4). Wrap any user- or content-supplied value
 * (a surah name, a city, a time) before inserting it into a sentence of a different direction,
 * so punctuation and digits keep their correct order.
 */
object Bidi {
    /** FIRST STRONG ISOLATE: the isolate takes the direction of its first strong character. */
    const val FSI = '⁨'

    /** POP DIRECTIONAL ISOLATE. */
    const val PDI = '⁩'

    fun isolate(text: String): String = "$FSI$text$PDI"

    /** Removes isolation marks, e.g. before copying text to the clipboard or comparing. */
    fun stripIsolates(text: String): String = text.filterNot { it == FSI || it == PDI }
}
