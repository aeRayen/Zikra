package com.zikra.tooling.content

import org.w3c.dom.Element
import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

data class Ayah(val surah: Int, val ayah: Int, val text: String)

data class Surah(
    val number: Int,
    val ayahCount: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val nameEnglish: String,
    val revelationPlace: String,
    val revelationOrder: Int,
)

/** Position marker from quran-data.xml (a juz, hizb quarter or page starts at surah:ayah). */
data class Marker(val index: Int, val surah: Int, val ayah: Int)

data class QuranMetadata(
    val surahs: List<Surah>,
    val juzStarts: List<Marker>,
    val quarterStarts: List<Marker>,
    val pageStarts: List<Marker>,
    val sajdas: List<Pair<Marker, String>>,
)

data class TanzilText(val ayahs: List<Ayah>, val licenseNotice: String)

/** Parses Tanzil's "surah|ayah|text" format. Text is kept exactly as in the file (never trimmed or changed). */
fun parseTanzilText(file: File): TanzilText {
    val ayahs = mutableListOf<Ayah>()
    val notice = StringBuilder()
    file.readLines(Charsets.UTF_8).forEachIndexed { lineNo, line ->
        when {
            line.startsWith("#") -> notice.appendLine(line)
            line.isEmpty() -> Unit
            else -> {
                val parts = line.split('|', limit = TANZIL_FIELDS)
                val surah = parts.getOrNull(0)?.toIntOrNull()
                val ayah = parts.getOrNull(1)?.toIntOrNull()
                ensure(parts.size == TANZIL_FIELDS && surah != null && ayah != null) {
                    "Line ${lineNo + 1}: expected surah|ayah|text"
                }
                ayahs += Ayah(surah!!, ayah!!, parts[2])
            }
        }
    }
    ensure(notice.isNotBlank()) { "Tanzil license notice missing from ${file.name}" }
    return TanzilText(ayahs, notice.toString().trimEnd())
}

fun parseQuranMetadata(file: File): QuranMetadata {
    val factory = DocumentBuilderFactory.newInstance().apply {
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    }
    val doc = factory.newDocumentBuilder().parse(file)

    fun elements(tag: String): List<Element> {
        val nodes = doc.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }
    fun Element.int(name: String) = getAttribute(name).toInt()
    fun Element.marker() = Marker(int("index"), int("sura"), int("aya"))

    return QuranMetadata(
        surahs = elements("sura").map {
            Surah(
                number = it.int("index"),
                ayahCount = it.int("ayas"),
                nameArabic = it.getAttribute("name"),
                nameTransliterated = it.getAttribute("tname"),
                nameEnglish = it.getAttribute("ename"),
                revelationPlace = if (it.getAttribute("type") == "Meccan") "makkah" else "madinah",
                revelationOrder = it.int("order"),
            )
        },
        juzStarts = elements("juz").map { it.marker() },
        quarterStarts = elements("quarter").map { it.marker() },
        pageStarts = elements("page").map { it.marker() },
        sajdas = elements("sajda").map { it.marker() to it.getAttribute("type") },
    )
}

private const val TANZIL_FIELDS = 3
