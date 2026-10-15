package com.zikra.core.i18n

/**
 * Digit shapes (ADR-019). Formatting is done with Western digits and then substituted, so the
 * result is identical on the JVM, on every Android version, and later on iOS.
 */
enum class NumeralSystem(private val zero: Char) {
    /** 0123456789 */
    LATN('0'),

    /** ٠١٢٣٤٥٦٧٨٩ — Arabic-Indic (Arabic). */
    ARAB('٠'),

    /** ۰۱۲۳۴۵۶۷۸۹ — Extended Arabic-Indic (Persian, Urdu). */
    ARABEXT('۰'),
    ;

    /** Replaces Western digits in [text] with this system's digits; other characters are kept. */
    fun apply(text: String): String {
        if (this == LATN) return text
        val offset = zero - '0'
        return buildString(text.length) {
            for (c in text) append(if (c in '0'..'9') c + offset else c)
        }
    }

    companion object {
        /** User preference from settings; [AUTO] follows the UI language. */
        const val AUTO = "auto"

        fun forLanguage(languageTag: String): NumeralSystem = when (languageTag.substringBefore('-').lowercase()) {
            "ar" -> ARAB
            "fa", "ur", "ps" -> ARABEXT
            else -> LATN
        }

        /** Resolves the stored setting (auto|latn|arab|arabext) for [languageTag]. */
        fun resolve(preference: String?, languageTag: String): NumeralSystem = when (preference?.lowercase()) {
            "latn" -> LATN
            "arab" -> ARAB
            "arabext" -> ARABEXT
            else -> forLanguage(languageTag)
        }

        /** Converts any Arabic-Indic or Extended digits back to Western digits (input parsing, search). */
        fun toLatin(text: String): String = buildString(text.length) {
            for (c in text) {
                append(
                    when (c) {
                        in '٠'..'٩' -> '0' + (c - '٠')
                        in '۰'..'۹' -> '0' + (c - '۰')
                        else -> c
                    },
                )
            }
        }
    }
}
