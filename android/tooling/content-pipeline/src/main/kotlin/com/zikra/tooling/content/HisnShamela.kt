package com.zikra.tooling.content

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.io.File

/**
 * Hisn al-Muslim as digitised by al-Maktaba al-Shamela (book 31307, numbering identical to the printed
 * edition). Used ONLY for the author's references (footnotes, «hamesh») of each dhikr (WP 2.14); the
 * dhikr texts themselves come from hisnmuslim.com and are cross-checked against this copy.
 */
data class ShamelaPage(val number: Int, val paragraphs: List<String>, val footnotes: List<String>)

object HisnShamela {
    private const val PAGE_MARK = "=== page "
    private const val PARAGRAPH = "P\t"
    private const val FOOTNOTE = "F\t"

    /** One saved page (https://shamela.ws/book/31307/<n>) -> its paragraphs and footnote lines, verbatim. */
    fun parse(html: String): ShamelaPage? {
        val body = Jsoup.parse(html).selectFirst("div.nass[data-page-num]")
        val number = body?.attr("data-page-num")?.toIntOrNull()
        if (body == null || number == null) return null
        val paragraphs = body.select(
            "p"
        ).filterNot { it.hasClass("hamesh") }.map { plainText(it) }.filter { it.isNotBlank() }
        val footnotes = body.select("p.hamesh").flatMap { p -> p.html().split(Regex("<br\\s*/?>")) }
            .map { Jsoup.parse(it).text().trim() }
            .filter { it.isNotEmpty() }
        return ShamelaPage(number, paragraphs, footnotes)
    }

    /** Text of a paragraph without the page's copy buttons; footnote markers stay as "(١)". */
    private fun plainText(p: Element): String {
        p.select("a.btn_tag").remove()
        val sb = StringBuilder()
        p.traverse { node, _ -> if (node is TextNode) sb.append(node.wholeText) }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** Canonical, diff-friendly text file pinned in content/sources (one line per paragraph / footnote). */
    fun write(pages: List<ShamelaPage>, out: File) {
        out.parentFile?.mkdirs()
        out.writeText(
            buildString {
                pages.sortedBy { it.number }.forEach { page ->
                    append(PAGE_MARK).append(page.number).append('\n')
                    page.paragraphs.forEach { append(PARAGRAPH).append(it).append('\n') }
                    page.footnotes.forEach { append(FOOTNOTE).append(it).append('\n') }
                }
            },
            Charsets.UTF_8,
        )
    }

    fun read(file: File): List<ShamelaPage> {
        val pages = mutableListOf<ShamelaPage>()
        var number = -1
        val paragraphs = mutableListOf<String>()
        val footnotes = mutableListOf<String>()
        fun flush() {
            if (number >= 0) pages += ShamelaPage(number, paragraphs.toList(), footnotes.toList())
            paragraphs.clear()
            footnotes.clear()
        }
        file.readLines(Charsets.UTF_8).forEach { line ->
            when {
                line.startsWith(PAGE_MARK) -> {
                    flush()
                    number = line.removePrefix(PAGE_MARK).trim().toInt()
                }
                line.startsWith(PARAGRAPH) -> paragraphs += line.removePrefix(PARAGRAPH)
                line.startsWith(FOOTNOTE) -> footnotes += line.removePrefix(FOOTNOTE)
            }
        }
        flush()
        return pages
    }
}
