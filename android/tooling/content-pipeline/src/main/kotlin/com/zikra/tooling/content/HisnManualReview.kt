package com.zikra.tooling.content

import com.zikra.core.i18n.NumeralSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

enum class ManualVerdict {
    /** The hadith is the evidence or the virtue of the dhikr, not its wording. */
    EVIDENCE,

    /** The same hadith, numbered differently in the independent copy ([ManualReviewItem.found]). */
    EDITION,

    /** The number in the book points to another hadith; [ManualReviewItem.correct] is the right one. */
    WRONG_NUMBER,

    /** A side remark of the footnote, not the dhikr's source. */
    SECONDARY,

    /** The independent copy has no text for that number; the dhikr is confirmed by its other citations. */
    COPY_GAP,
}

@Serializable
data class ManualReviewItem(
    val dhikr: Int,
    val book: String,
    val number: Int,
    val verdict: ManualVerdict,
    val note: String,
    val found: Int? = null,
    val correct: Int? = null,
)

/** content/review/HISN_MANUAL_REVIEW.json: a verdict for every citation the automatic check left open (ADR-042). */
@Serializable
data class ManualReview(val comment: String, val reviewed: String, val items: List<ManualReviewItem>) {

    fun verdict(dhikr: Int, c: Citation): ManualReviewItem? =
        items.firstOrNull { it.dhikr == dhikr && it.book == c.book.key && it.number == c.number }

    /**
     * A correction shown next to the author's reference in the app (the author's text itself is never
     * changed): «تصحيح: هذا الحديث في صحيح مسلم برقم ٢٧٢٧، لا ٢٧٢٦».
     */
    fun corrections(): Map<Int, List<String>> =
        items.filter { it.verdict == ManualVerdict.WRONG_NUMBER }.groupBy({ it.dhikr }) { item ->
            val book = HadithBook.entries.first { it.key == item.book }
            val right = NumeralSystem.ARAB.apply(requireNotNull(item.correct).toString())
            val wrong = NumeralSystem.ARAB.apply(item.number.toString())
            "تصحيح: هذا الحديث في ${book.arabic} برقم $right، لا $wrong."
        }

    companion object {
        const val FILE = "review/HISN_MANUAL_REVIEW.json"

        fun load(contentDir: File): ManualReview =
            Json.decodeFromString(serializer(), File(contentDir, FILE).readText(Charsets.UTF_8))
    }
}

/** The automatic check, the manual verdicts on what it left open, and what is still open (ADR-042). */
data class ReviewedVerification(
    val results: List<DhikrVerification>,
    val hadiths: CitedHadiths,
    val review: ManualReview,
) {
    private val needsReview = setOf(CitationCheck.MISMATCH, CitationCheck.NOT_IN_COPY)

    /** Citations the automatic check could not confirm and nobody has looked at yet. */
    val open: List<Pair<Int, Citation>> = results.flatMap { r ->
        r.citations.filter { it.result in needsReview && review.verdict(r.dhikr.id, it.citation) == null }
            .map { r.dhikr.id to it.citation }
    }

    /** Verdicts about citations that are no longer open (the sources or the check changed): must be removed. */
    val stale: List<ManualReviewItem> = review.items.filter { item ->
        results.none { r ->
            r.dhikr.id == item.dhikr && r.citations.any {
                it.result in needsReview && it.citation.book.key == item.book && it.citation.number == item.number
            }
        }
    }

    /** Every open citation has a verdict, and every verdict is about an open citation. */
    fun ensureComplete() {
        ensure(open.isEmpty()) {
            val list = open.joinToString { (d, c) -> "$d:${c.book.key} ${c.number}" }
            "Citations to review in ${ManualReview.FILE}: $list"
        }
        ensure(stale.isEmpty()) {
            "Stale verdicts in ${ManualReview.FILE}: " + stale.joinToString { "${it.dhikr}:${it.book} ${it.number}" }
        }
    }
}
