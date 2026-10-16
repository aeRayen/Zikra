package com.zikra.tooling.content

import java.io.File

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
        lock.verifiedDirectory(sourcesDir, Pipeline.HISN_AR)
        val adhkar = parseHisnMuslim(File(sourcesDir, lock.source(Pipeline.HISN_AR).path))
        ensure(
            adhkar.size == HisnReferences.DHIKR_COUNT
        ) { "Expected ${HisnReferences.DHIKR_COUNT} adhkar, got ${adhkar.size}" }
        val pages = HisnShamela.read(lock.verifiedFile(sourcesDir, Pipeline.SHAMELA_HISN))
        return HisnReferences.extract(pages, adhkar)
    }

    private companion object {
        const val MIN_PAGES = 150
    }
}
