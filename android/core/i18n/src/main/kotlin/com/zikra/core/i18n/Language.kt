package com.zikra.core.i18n

enum class TextDirection { RTL, LTR }

/**
 * A language the app can be shown in. [tag] is BCP 47 (I18N_SPEC_AR.md §1);
 * [nativeName] is how the language names itself, which is how it appears in the picker.
 */
data class Language(
    val tag: String,
    val nativeName: String,
    val direction: TextDirection,
) {
    init {
        require(BCP47.matches(tag)) { "Invalid BCP 47 tag: $tag" }
    }

    private companion object {
        val BCP47 = Regex("^[a-z]{2,3}(-[A-Za-z0-9]{2,8})*$")
    }
}

/**
 * Languages enabled in this build. Adding a language = adding resources + one entry here,
 * after it meets the activation checklist (I18N_SPEC_AR.md §11). Wave 1: Arabic, English.
 */
object SupportedLanguages {
    val ARABIC = Language("ar", "العربية", TextDirection.RTL)
    val ENGLISH = Language("en", "English", TextDirection.LTR)

    val all: List<Language> = listOf(ARABIC, ENGLISH)

    /** Used when the device language is not supported (I18N_SPEC_AR.md §2). */
    val fallback: Language = ENGLISH

    private val RTL_LANGUAGES = setOf("ar", "fa", "ur", "he", "ps", "sd", "ug", "yi", "ckb", "dv")

    /** Exact tag, then base language ("ar-EG" -> "ar"), then [fallback]. */
    fun resolve(tag: String?): Language {
        if (tag.isNullOrBlank()) return fallback
        val base = tag.substringBefore('-').lowercase()
        return all.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
            ?: all.firstOrNull { it.tag == base }
            ?: fallback
    }

    /** Direction for any tag, including languages not enabled yet (e.g. user content). */
    fun directionOf(tag: String): TextDirection =
        if (tag.substringBefore('-').lowercase() in RTL_LANGUAGES) TextDirection.RTL else TextDirection.LTR
}
