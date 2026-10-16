package com.zikra.tooling.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One dhikr of hisnmuslim.com in another language: its meaning and its pronunciation in Latin letters. */
data class DhikrTranslation(
    val id: Int,
    val chapter: Int,
    val chapterTitle: String,
    /** The Arabic text this translation was made for: checked against the displayed Arabic. */
    val arabic: String,
    val transliteration: String,
    val meaning: String,
)

/** Reads hisnmuslim.com/api/<lang>/<chapter>.json files (1..132), verbatim. */
fun parseHisnMuslimTranslation(dir: File): List<DhikrTranslation> =
    (1..HisnReferences.CHAPTER_COUNT).flatMap { chapter ->
        val raw = File(dir, "$chapter.json").readText(Charsets.UTF_8).removePrefix("﻿")
        Json.parseToJsonElement(repairTitle(raw)).jsonObject.entries.flatMap { (title, items) ->
            items.jsonArray.map { item ->
                val o = item.jsonObject
                fun text(key: String) = o[key]?.jsonPrimitive?.content.orEmpty().trim()
                DhikrTranslation(
                    id = o.getValue("ID").jsonPrimitive.int,
                    chapter = chapter,
                    chapterTitle = title.trim(),
                    // en/132.json writes the Arabic of dhikr 267 under "Text"; it is still checked below.
                    arabic = text("ARABIC_TEXT").ifEmpty { text("Text") },
                    transliteration = text("LANGUAGE_ARABIC_TRANSLATED_TEXT"),
                    meaning = text("TRANSLATED_TEXT"),
                )
            }
        }
    }.sortedBy { it.id }

/**
 * The only defect found in the files (en/126.json): a chapter title without its closing quote,
 * `"What to say when you feel frightened:`. The file stays byte for byte as published (its hash is
 * pinned); only that quote is added when reading. Any other malformed JSON still fails.
 */
internal fun repairTitle(raw: String): String =
    raw.replace(Regex("""(?m)^(\s*"[^"\n]*[^"\s]):\s*$""")) { m -> m.groupValues[1] + "\":" }

/**
 * A translation is attached to a dhikr only when it was made for that dhikr (ADR-044): same number, same
 * chapter, the Arabic it carries agrees with the displayed Arabic, and it has a meaning.
 */
object HisnTranslations {
    const val MIN_AGREEMENT = 0.9

    /** Every reason a translation cannot be attached, by dhikr; empty when all are sound. */
    fun problems(adhkar: List<HisnDhikr>, translated: List<DhikrTranslation>): Map<Int, String> {
        val byId = translated.associateBy { it.id }
        return adhkar.mapNotNull { d ->
            val t = byId[d.id]
            val agreement = t?.let { HisnReferences.agreement(d.text, it.arabic) } ?: 0.0
            val problem = when {
                t == null -> "missing"
                t.chapter != d.chapter -> "in chapter ${t.chapter}, not ${d.chapter}"
                agreement < MIN_AGREEMENT -> "Arabic differs (agreement %.2f)".format(agreement)
                t.meaning.isEmpty() -> "no meaning"
                else -> null
            }
            problem?.let { d.id to it }
        }.toMap()
    }
}
