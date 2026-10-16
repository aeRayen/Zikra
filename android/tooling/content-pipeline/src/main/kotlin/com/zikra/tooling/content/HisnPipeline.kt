package com.zikra.tooling.content

import java.io.File

/** The adhkar as bundled: texts, their displayed parts by dhikr id, and the verified references. */
data class AdhkarBook(
    val adhkar: List<HisnDhikr>,
    val parts: Map<Int, List<DhikrPart>>,
    val references: List<DhikrReference>,
    /** Corrections found by the independent check (ADR-042), shown next to the author's references. */
    val notes: Map<Int, List<String>> = emptyMap(),
)

data class AdhkarSources(val text: PinnedSource, val references: PinnedSource, val quran: PinnedSource)

/** Hisn al-Muslim sources (WP 2.14, ADR-040): pinned hisnmuslim.com texts + the Shamela copy's references. */
class HisnPipeline(private val sourcesDir: File) {
    private val lock = SourceLock.load(sourcesDir)

    /** Converts saved Shamela pages (p1.html ... p151.html) into the pinned canonical text (WP 2.14). */
    fun extractShamelaHisn(htmlDir: File) {
        val pages = naturalOrder(
            htmlDir
        ).filter { it.extension == "html" }.mapNotNull { HisnShamela.parse(it.readText()) }
        ensure(pages.size >= MIN_PAGES) { "Only ${pages.size} Shamela pages parsed" }
        HisnShamela.write(pages, File(sourcesDir, lock.source(Pipeline.SHAMELA_HISN).path))
    }

    /** Every dhikr with the author's footnotes, cross-checked against the displayed text (WP 2.14). */
    fun hisnReferences(): List<DhikrReference> {
        val adhkar = adhkar()
        val pages = HisnShamela.read(lock.verifiedFile(sourcesDir, Pipeline.SHAMELA_HISN))
        return HisnReferences.extract(pages, adhkar)
    }

    /** Displayed parts of every dhikr: quoted verses located in the pinned Tanzil texts (WP 2.14b). */
    fun adhkarBook(): AdhkarBook {
        val references = hisnReferences()
        val unverified = references.filter { it.status != ReferenceStatus.VERIFIED.name }
        ensure(unverified.isEmpty()) {
            "References not verified (see hisn-review): ${unverified.joinToString { "${it.id}:${it.status}" }}"
        }
        val adhkar = adhkar()
        val quran = HisnQuran(
            simple = parseTanzilText(lock.verifiedFile(sourcesDir, Pipeline.QURAN_SIMPLE)).ayahs,
            uthmani = parseTanzilText(lock.verifiedFile(sourcesDir, Pipeline.QURAN_TEXT)).ayahs,
        )
        return AdhkarBook(adhkar, adhkar.associate { it.id to quran.parts(it.id, it.text) }, references)
    }

    /** adhkar.sqlite bundled in the APK (WP 2.14b). */
    fun buildAdhkarBundle(out: File) {
        val sources = AdhkarSources(
            text = lock.source(Pipeline.HISN_AR),
            references = lock.source(Pipeline.SHAMELA_HISN),
            quran = lock.source(Pipeline.QURAN_TEXT),
        )
        // Fail-closed: every reference the independent check could not confirm has been reviewed (ADR-042).
        val verified = verifyReferences()
        verified.ensureComplete()
        PackWriter.writeAdhkar(out, adhkarBook().copy(notes = verified.review.corrections()), sources)
    }

    /** Takes the cited hadiths out of the six books downloaded from hadith-api into [booksDir] (ADR-042). */
    fun extractCitedHadiths(booksDir: File) {
        val cited = HisnVerification.extract(hisnReferences(), booksDir)
        HisnVerification.writeCited(cited, File(sourcesDir, lock.source(Pipeline.HADITH_CITED).path))
    }

    /** Independent check of every reference against the six books and the Tanzil text (ADR-042). */
    fun verifyReferences(): ReviewedVerification {
        val cited = HisnVerification.loadCited(lock.verifiedFile(sourcesDir, Pipeline.HADITH_CITED))
        val names = parseQuranMetadata(lock.verifiedFile(sourcesDir, Pipeline.QURAN_METADATA))
            .surahs.associate { it.number to it.nameArabic }
        val review = ManualReview.load(sourcesDir.parentFile)
        return ReviewedVerification(HisnVerification.verify(adhkarBook(), cited, names), cited, review)
    }

    private fun adhkar(): List<HisnDhikr> {
        lock.verifiedDirectory(sourcesDir, Pipeline.HISN_AR)
        val adhkar = parseHisnMuslim(File(sourcesDir, lock.source(Pipeline.HISN_AR).path))
        ensure(
            adhkar.size == HisnReferences.DHIKR_COUNT
        ) { "Expected ${HisnReferences.DHIKR_COUNT} adhkar, got ${adhkar.size}" }
        return adhkar
    }

    private companion object {
        const val MIN_PAGES = 150
    }
}
