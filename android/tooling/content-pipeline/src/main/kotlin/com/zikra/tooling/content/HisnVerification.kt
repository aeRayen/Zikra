package com.zikra.tooling.content

import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.SearchNormalizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** One hadith of the independent copy, verbatim; [number] as it numbers it (Muslim: "2711", "2711.02"). */
@Serializable
data class CitedHadith(val book: String, val number: String, val text: String)

@Serializable
data class UpstreamFile(val book: String, val url: String, val sha256: String)

/**
 * The hadiths cited by Hisn al-Muslim, taken out of fawazahmed0/hadith-api (Unlicense): small enough to
 * pin in the repository, and anyone can re-download the six books ([upstream] hashes) and re-extract.
 */
@Serializable
data class CitedHadiths(val upstream: List<UpstreamFile>, val hadiths: List<CitedHadith>)

enum class CitationCheck {
    /** The hadith with that number contains the dhikr's words. */
    MATCH,

    /** A hadith a few numbers away contains them: this copy numbers that part of the book differently. */
    MATCH_NEAR,

    /** It exists but does not contain them: someone must look. */
    MISMATCH,

    /** The independent copy has no hadith with that number. */
    NOT_IN_COPY,
}

data class CheckedCitation(
    val citation: Citation,
    val result: CitationCheck,
    /** Share of the dhikr's words found in the hadith. */
    val containment: Double,
    /** Longest run of the dhikr's words found in the hadith in the same order. */
    val run: Int,
    /** For [CitationCheck.MATCH_NEAR]: the number of the matching hadith in the independent copy. */
    val foundAt: Int? = null,
)

enum class DhikrCheck {
    /** At least one cited hadith (or the cited verse) confirms it, and nothing contradicts it. */
    CONFIRMED,

    /** Confirmed by one citation, but another citation does not contain it. */
    CONFIRMED_WITH_NOTES,

    /** Citations of the six books exist, but none contains it. */
    NOT_CONFIRMED,

    /** Only works outside the six books are cited (Ahmad, al-Hakim...): cannot be checked here. */
    OUTSIDE_SIX_BOOKS,
}

data class DhikrVerification(
    val dhikr: HisnDhikr,
    val footnotes: List<String>,
    val citations: List<CheckedCitation>,
    /** Verses shown in the dhikr whose surah and ayah number the footnotes also give. */
    val versesCited: Int,
    val verses: Int,
    val result: DhikrCheck,
)

/**
 * Independent check of the references (WP 2.14, ADR-042): every hadith number the author gives in the six
 * books is looked up in another copy of those books, which must contain the words of the dhikr; every
 * verse shown is checked against the surah and ayah the footnotes name.
 */
object HisnVerification {
    const val MIN_CONTAINMENT = 0.6
    const val MIN_RUN = 5

    /** How far apart two editions may number the same hadith (seen: Abu Dawud 5075 here is 5073 there). */
    const val NEAR = 5
    private const val NEAR_RUN = 3
    private const val LONG_RUN = 7
    private const val MIN_WORD = 3
    const val UPSTREAM = "https://cdn.jsdelivr.net/gh/fawazahmed0/hadith-api@1/editions/ara-"

    private val alefHamza = Regex("[اأإآٱءؤئ]")
    private val numbers = Regex("([0-9٠-٩]+)(?:\\s*[-–]\\s*([0-9٠-٩]+))?")

    /** Takes the cited hadiths (all variants of each number) out of the six downloaded books. */
    fun extract(references: List<DhikrReference>, booksDir: File): CitedHadiths {
        val cited = references.flatMap { r -> r.footnotes.flatMap(HisnCitations::parse) }.toSet()
        val upstream = mutableListOf<UpstreamFile>()
        val hadiths = mutableListOf<CitedHadith>()
        for (book in HadithBook.entries) {
            val file = File(booksDir, "ara-${book.key}.json")
            ensure(file.isFile) { "Missing ${file.path} (download $UPSTREAM${book.key}.json)" }
            upstream += UpstreamFile(book.key, "$UPSTREAM${book.key}.json", sha256Hex(file.readBytes()))
            val wanted = cited.filter { it.book == book }.flatMap { (it.number - NEAR)..(it.number + NEAR) }.toSet()
            Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject.getValue("hadiths").jsonArray
                .map { it.jsonObject }
                .forEach { h ->
                    // A few records are empty placeholders (no number, no text): skipped.
                    val number = h["arabicnumber"]?.jsonPrimitive?.content.orEmpty()
                    val text = h["text"]?.jsonPrimitive?.content.orEmpty()
                    if (text.isNotBlank() && number.substringBefore('.').toIntOrNull() in wanted) {
                        hadiths += CitedHadith(book.key, number, text)
                    }
                }
        }
        return CitedHadiths(upstream, hadiths)
    }

    fun verify(
        book: AdhkarBook,
        hadiths: CitedHadiths,
        surahNames: Map<Int, String>,
    ): List<DhikrVerification> {
        val byCitation = hadiths.hadiths.groupBy { h ->
            HadithBook.entries.first { it.key == h.book } to h.number.substringBefore('.').toInt()
        }
        val references = book.references.associateBy { it.id }
        return book.adhkar.map { dhikr ->
            val footnotes = references[dhikr.id]?.footnotes.orEmpty()
            val checked = footnotes.flatMap(HisnCitations::parse).distinct().map { c ->
                checkNear(c, dhikr.text) { n -> byCitation[c.book to n] }
            }
            val verses = book.parts.getValue(dhikr.id).filterIsInstance<DhikrPart.Quran>()
            val cited = verses.count { verseCited(it, footnotes, surahNames) }
            DhikrVerification(dhikr, footnotes, checked, cited, verses.size, judge(checked, cited, verses.size))
        }
    }

    /** The cited number first; if it does not confirm, the nearest hadith within [NEAR] numbers that does. */
    private fun checkNear(c: Citation, dhikrText: String, hadithsAt: (Int) -> List<CitedHadith>?): CheckedCitation {
        val exact = check(c, dhikrText, hadithsAt(c.number))
        if (exact.result == CitationCheck.MATCH) return exact
        val near = (1..NEAR).asSequence().flatMap { d -> sequenceOf(c.number - d, c.number + d) }
            .mapNotNull { n -> hadithsAt(n)?.let { n to check(c, dhikrText, it) } }
            .firstOrNull { (_, found) -> found.result == CitationCheck.MATCH && strongNear(found, dhikrText) }
        return near?.let { (n, found) -> found.copy(result = CitationCheck.MATCH_NEAR, foundAt = n) } ?: exact
    }

    /**
     * A neighbour must agree more than the cited number has to: most of the words AND a run, or a long
     * run, so a dua that merely shares an opening («اللهم إني أعوذ بك من») is not taken for it.
     */
    private fun strongNear(found: CheckedCitation, dhikrText: String): Boolean {
        val size = sequence(dhikrText).size
        return (found.containment >= MIN_CONTAINMENT && found.run >= minOf(NEAR_RUN, size)) || found.run >= LONG_RUN
    }

    /**
     * A cited hadith confirms the dhikr if it holds most of its words, or [MIN_RUN] of them in a row and in
     * the same order (a dhikr gathered from several narrations shares a long run with each of them).
     */
    private fun check(c: Citation, dhikrText: String, found: List<CitedHadith>?): CheckedCitation {
        if (found == null) return CheckedCitation(c, CitationCheck.NOT_IN_COPY, 0.0, 0)
        val dhikr = sequence(dhikrText)
        val distinct = dhikr.filter { it.length >= MIN_WORD }.toSet()
        val hadith = found.map { sequence(it.text) }
        val hadithWords = hadith.flatMapTo(HashSet()) { it }
        val present = distinct.count { it in hadithWords }
        val containment = if (distinct.isEmpty()) 0.0 else present.toDouble() / distinct.size
        val run = hadith.maxOf { longestCommonRun(dhikr, it) }
        val matched = containment >= MIN_CONTAINMENT || run >= minOf(MIN_RUN, dhikr.size)
        return CheckedCitation(c, if (matched) CitationCheck.MATCH else CitationCheck.MISMATCH, containment, run)
    }

    private fun judge(checked: List<CheckedCitation>, versesCited: Int, verses: Int): DhikrCheck {
        val confirming = setOf(CitationCheck.MATCH, CitationCheck.MATCH_NEAR)
        val matched = checked.any { it.result in confirming } || (verses > 0 && versesCited == verses)
        val contradicted = checked.any { it.result == CitationCheck.MISMATCH }
        return when {
            matched && !contradicted -> DhikrCheck.CONFIRMED
            matched -> DhikrCheck.CONFIRMED_WITH_NOTES
            checked.isNotEmpty() -> DhikrCheck.NOT_CONFIRMED
            else -> DhikrCheck.OUTSIDE_SIX_BOOKS
        }
    }

    /** The footnotes name the verse's surah and give its ayah number (alone or in a range «١٩٠ - ٢٠٠»). */
    private fun verseCited(verse: DhikrPart.Quran, footnotes: List<String>, surahNames: Map<Int, String>): Boolean {
        val name = surahNames[verse.surah] ?: return false
        return footnotes.any { note ->
            val at = note.indexOf("سورة $name").takeIf { it >= 0 } ?: note.indexOf(name).takeIf { it >= 0 }
            at != null && numbers.findAll(note.substring(at)).any { m ->
                val from = NumeralSystem.toLatin(m.groupValues[1]).toInt()
                val to = m.groupValues[2].takeIf { it.isNotEmpty() }?.let { NumeralSystem.toLatin(it).toInt() } ?: from
                verse.ayah in from..to
            }
        }
    }

    /** Words in order, spelling-independent (no marks, no alef or hamza seats). */
    private fun sequence(text: String): List<String> =
        SearchNormalizer.normalize(text.replace(alefHamza, ""))
            .map { if (it.isLetter()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.length >= 2 }

    fun loadCited(file: File): CitedHadiths =
        Json.decodeFromString(CitedHadiths.serializer(), file.readText(Charsets.UTF_8))

    fun writeCited(cited: CitedHadiths, file: File) {
        file.parentFile?.mkdirs()
        val json = Json { prettyPrint = true }
        file.writeText(json.encodeToString(CitedHadiths.serializer(), cited) + "\n", Charsets.UTF_8)
    }
}
