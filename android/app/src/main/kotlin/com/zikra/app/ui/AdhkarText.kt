package com.zikra.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.zikra.core.adhkar.DhikrPart

/**
 * The dhikr as one paragraph: its own words in the UI font, quoted verses in the mushaf font (Tanzil
 * Uthmani text) each followed by its ayah number mark.
 */
internal fun dhikrText(parts: List<DhikrPart>, quranStyle: SpanStyle): AnnotatedString = buildAnnotatedString {
    parts.forEachIndexed { i, part ->
        if (i > 0) append(' ')
        when (part) {
            is DhikrPart.Words -> append(part.text)
            is DhikrPart.Quran -> withStyle(quranStyle) {
                append(part.text)
                append(' ')
                append(ayahNumberMark(part.ayah))
            }
        }
    }
}

/** The footnote without the book's marker «(١)», which points into the printed page. */
internal fun withoutMarker(reference: String): String = reference.replaceFirst(footnoteMarker, "")

private val footnoteMarker = Regex("^\\(\\s*[0-9٠-٩]+\\s*\\)\\s*")

internal const val ADHKAR_LIST_TAG = "adhkar_list"
internal const val ADHKAR_RESET_TAG = "adhkar_reset"
internal const val ADHKAR_PROGRESS_TAG = "adhkar_progress"
internal fun dhikrTag(id: Int) = "dhikr_$id"
internal fun countTag(id: Int) = "dhikr_count_$id"
internal fun referenceTag(id: Int) = "dhikr_reference_$id"
internal fun referenceTextTag(id: Int) = "dhikr_reference_text_$id"

/** The title in the UI language when the source has one (first letter capitalised), else the Arabic. */
internal fun displayTitle(chapter: com.zikra.core.adhkar.AdhkarChapter): String =
    chapter.translatedTitle?.replaceFirstChar { it.uppercaseChar() } ?: chapter.title
internal fun meaningTag(id: Int) = "dhikr_meaning_$id"
