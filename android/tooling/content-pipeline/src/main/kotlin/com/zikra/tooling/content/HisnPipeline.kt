package com.zikra.tooling.content

import java.io.File

/** The adhkar as bundled: texts, their displayed parts by dhikr id, and the verified references. */
data class AdhkarBook(
    val adhkar: List<HisnDhikr>,
    val parts: Map<Int, List<DhikrPart>>,
    val references: List<DhikrReference>,
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
        PackWriter.writeAdhkar(out, adhkarBook(), sources)
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
