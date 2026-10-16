package com.zikra.tooling.content

import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.SearchNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One dhikr of hisnmuslim.com (the displayed text source). */
data class HisnDhikr(val id: Int, val chapter: Int, val chapterTitle: String, val text: String, val repeat: Int)

/** Reads hisnmuslim.com/api/ar/<chapter>.json files (1..132), verbatim. */
fun parseHisnMuslim(dir: File): List<HisnDhikr> =
    (1..HisnReferences.CHAPTER_COUNT).flatMap { chapter ->
        val raw = File(dir, "$chapter.json").readText(Charsets.UTF_8).removePrefix("﻿")
        val root = Json.parseToJsonElement(raw).jsonObject
        root.entries.flatMap { (title, items) ->
            items.jsonArray.map { item ->
                val o = item.jsonObject
                HisnDhikr(
                    id = o.getValue("ID").jsonPrimitive.int,
                    chapter = chapter,
                    chapterTitle = title.trim(),
                    text = o.getValue("ARABIC_TEXT").jsonPrimitive.content,
                    repeat = (o["REPEAT"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 1,
                )
            }
        }
    }.sortedBy { it.id }

/** How far the Shamela copy of a dhikr agrees with the displayed hisnmuslim text. */
enum class ReferenceStatus {
    /** Same dhikr (texts agree) and at least one footnote: the reference can be shown. */
    VERIFIED,

    /** Texts differ noticeably: a human must check before the reference is shown. */
    TEXT_DIFFERS,

    /** The dhikr was found but has no footnote. */
    NO_REFERENCE,

    /** Footnotes exist but none names a recognisable source (book + number, or a surah): check by hand. */
    SOURCE_UNCLEAR,

    /** The dhikr number was not found in the Shamela copy. */
    NOT_FOUND,
}

@Serializable
data class DhikrReference(
    val id: Int,
    val status: String,
    /** The author's footnotes for this dhikr, verbatim, in order. */
    val footnotes: List<String>,
    val shamelaPages: List<Int>,
    /** Share of the shorter text's words found in the other (0..1). */
    val agreement: Double,
)

/**
 * Links every dhikr to the author's footnotes by its number in the book, and cross-checks its text
 * against hisnmuslim.com, so a reference is only ever attached to the dhikr it belongs to.
 */
object HisnReferences {
    const val CHAPTER_COUNT = 132
    const val DHIKR_COUNT = 267
    const val MIN_AGREEMENT = 0.8

    private val numbered = Regex("^([0-9٠-٩]+)\\s*[-–]\\s*(.*)$")
    private val marker = Regex("\\(([0-9٠-٩]+)\\)")

    /** A hadith source with a number, or a Quran citation. */
    private val source = Regex(
        "(برقم|البخاري|مسلم|أبو داود|الترمذي|النسائي|ابن ماجه|أحمد|الحاكم|الطبراني|البيهقي|الموطأ|" +
            "ابن أبي شيبة|ابن السني|ابن حبان|الدارمي|النووي|أخرجه|رواه|قاله|سورة)",
    )
    private val chapterOrder = Regex("^\\s*\\(([0-9٠-٩]+)\\)\\s*")
    private val footnoteLine = Regex("^\\(([0-9٠-٩]+)\\)\\s*(.*)$")
    private val nonWord = Regex("[^\\p{L}\\s]")

    private class Collected(val id: Int) {
        val text = StringBuilder()
        val footnotes = mutableListOf<String>()
        val pages = sortedSetOf<Int>()
    }

    fun extract(pages: List<ShamelaPage>, adhkar: List<HisnDhikr>): List<DhikrReference> {
        val chapterTitles = adhkar.groupBy { it.chapter }.mapValues { it.value.first().chapterTitle }
        val collected = mutableMapOf<Int, Collected>()
        var nextChapter = 1
        var nextDhikr = 1
        var current: Collected? = null
        val ordered = pages.sortedBy { it.number }
        val notesByPage = footnotesByPage(ordered)
        for (page in ordered) {
            val notes = notesByPage.getValue(page.number)
            for (paragraph in page.paragraphs) {
                val m = numbered.find(paragraph)
                val number = m?.groupValues?.get(1)?.let { NumeralSystem.toLatin(it).toInt() }
                val rest = m?.groupValues?.get(2).orEmpty()
                when {
                    number == nextChapter && similar(rest, chapterTitles[nextChapter].orEmpty()) -> {
                        nextChapter++
                        current = null
                    }
                    number == nextDhikr -> {
                        current = Collected(nextDhikr).also { collected[it.id] = it }
                        nextDhikr++
                        // "٧٥ - (١) ...": the "(١)" right after the number is the dhikr's ORDER within its chapter,
                        // not a footnote marker; the footnote markers come after the text.
                        current.append(rest.replaceFirst(chapterOrder, ""), page.number, notes)
                    }
                    number == null -> current?.append(paragraph, page.number, notes)
                }
            }
        }
        return adhkar.map { dhikr -> judge(dhikr, collected[dhikr.id]) }
    }

    private fun Collected.append(paragraph: String, page: Int, notes: Map<Int, String>) {
        text.append(' ').append(paragraph)
        pages += page
        marker.findAll(paragraph).forEach { m ->
            notes[NumeralSystem.toLatin(m.groupValues[1]).toInt()]?.let { if (it !in footnotes) footnotes += it }
        }
    }

    /**
     * Per page, "(١) text" footnotes keyed by number. A line without a number continues the previous
     * footnote; at the top of a page it continues the LAST footnote of the previous page (long notes run
     * over a page break), so it is appended there before any dhikr reads it.
     */
    private fun footnotesByPage(pages: List<ShamelaPage>): Map<Int, Map<Int, String>> {
        val result = linkedMapOf<Int, Map<Int, String>>()
        var previous: LinkedHashMap<Int, String>? = null
        for (page in pages) {
            val notes = linkedMapOf<Int, String>()
            var last: Int? = null
            for (line in page.footnotes) {
                val m = footnoteLine.find(line)
                val owner = previous
                when {
                    m != null -> {
                        last = NumeralSystem.toLatin(m.groupValues[1]).toInt()
                        notes[last] = line
                    }
                    last != null -> notes[last] = notes.getValue(last) + " " + line
                    owner != null && owner.isNotEmpty() -> {
                        val key = owner.keys.last()
                        owner[key] = owner.getValue(key) + " " + line
                    }
                }
            }
            result[page.number] = notes
            previous = notes
        }
        return result
    }

    private fun judge(dhikr: HisnDhikr, found: Collected?): DhikrReference {
        if (found == null) {
            return DhikrReference(
                dhikr.id,
                ReferenceStatus.NOT_FOUND.name,
                emptyList(),
                emptyList(),
                0.0
            )
        }
        val agreement = agreement(dhikr.text, found.text.toString())
        val status = when {
            found.footnotes.isEmpty() -> ReferenceStatus.NO_REFERENCE
            found.footnotes.none { source.containsMatchIn(it) } -> ReferenceStatus.SOURCE_UNCLEAR
            agreement < MIN_AGREEMENT -> ReferenceStatus.TEXT_DIFFERS
            else -> ReferenceStatus.VERIFIED
        }
        return DhikrReference(dhikr.id, status.name, found.footnotes, found.pages.toList(), agreement)
    }

    private fun words(text: String): Set<String> =
        SearchNormalizer.normalize(
            text.replace(marker, " ")
        ).replace(nonWord, " ").split(' ').filter { it.length > 1 }.toSet()

    internal fun agreement(a: String, b: String): Double {
        val wa = words(a)
        val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return 0.0
        return wa.intersect(wb).size.toDouble() / minOf(wa.size, wb.size)
    }

    private fun similar(a: String, b: String) = agreement(a, b) >= MIN_AGREEMENT
}
