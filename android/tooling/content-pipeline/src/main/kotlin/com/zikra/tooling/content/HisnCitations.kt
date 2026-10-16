package com.zikra.tooling.content

import com.zikra.core.i18n.NumeralSystem

/** The six books checked against an independent copy (fawazahmed0/hadith-api, Unlicense). */
enum class HadithBook(val key: String, val arabic: String) {
    BUKHARI("bukhari", "صحيح البخاري"),
    MUSLIM("muslim", "صحيح مسلم"),
    ABU_DAWUD("abudawud", "سنن أبي داود"),
    TIRMIDHI("tirmidhi", "سنن الترمذي"),
    NASAI("nasai", "سنن النسائي"),
    IBN_MAJAH("ibnmajah", "سنن ابن ماجه"),
}

/** "Book, number" as written in the author's footnote (Bukhari: Fath al-Bari, Muslim: Abd al-Baqi). */
data class Citation(val book: HadithBook, val number: Int)

/**
 * Reads hadith citations out of a footnote of Hisn al-Muslim. A number written after «برقم» belongs to
 * the last of the six books named before it, and only if no other work was named in between: «النسائي في
 * عمل اليوم والليلة برقم ١٠٠», «صحيح الترمذي» (al-Albani's book) or «وتقدم تخريجه برقم ٣١» (a dhikr
 * number) have another numbering and are not checked against the six books.
 */
object HisnCitations {
    private const val BOUNDARY_BEFORE = "(?<![\\p{L}\\p{M}])و?"
    private const val BOUNDARY_AFTER = "(?![\\p{L}\\p{M}])"

    private val books = linkedMapOf(
        "البخاري" to HadithBook.BUKHARI,
        "مسلم" to HadithBook.MUSLIM,
        "أبو داود" to HadithBook.ABU_DAWUD,
        "أبي داود" to HadithBook.ABU_DAWUD,
        "الترمذي" to HadithBook.TIRMIDHI,
        "النسائي" to HadithBook.NASAI,
        "ابن ماجه" to HadithBook.IBN_MAJAH,
    )

    /** Other works and other numberings: a number after them is not one of the six books'. */
    private val others = listOf(
        "صحيح", "ضعيف", "أحمد", "الحاكم", "ابن حبان", "الطبراني", "البيهقي", "ابن السني", "الدارمي",
        "الموطأ", "مالك", "ابن أبي شيبة", "عبد الرزاق", "البغوي", "عمل اليوم والليلة", "الكبرى",
        "الأدب المفرد", "ابن خزيمة", "أبو يعلى", "البزار", "سعيد بن منصور", "تقدم", "سيأتي", "تخريجه", "الحديث", "ص",
    )

    /** al-Albani's «صحيح/ضعيف أبي داود» etc. have their own numbering: one token, before the book names. */
    private val albani = listOf("صحيح", "ضعيف").flatMap { k ->
        listOf("أبي داود", "الترمذي", "النسائي", "ابن ماجه", "الجامع", "الترغيب").map { "$k $it" }
    }

    private val token = Regex(
        BOUNDARY_BEFORE + "(" + (albani + books.keys + others).joinToString("|") + ")" + BOUNDARY_AFTER +
            "|(?<![\\p{L}\\p{M}])ب?رقم\\s*[:،,]?\\s*([0-9٠-٩]+(?:\\s*[،,]?\\s*و\\s*[0-9٠-٩]+)*)",
    )
    private val digits = Regex("[0-9٠-٩]+")

    fun parse(footnote: String): List<Citation> {
        val found = mutableListOf<Citation>()
        var current: HadithBook? = null
        for (m in token.findAll(footnote)) {
            val name = m.groupValues[1]
            val numbers = m.groupValues[2]
            when {
                name.isNotEmpty() -> current = books[name]
                current != null -> {
                    val book = current
                    digits.findAll(numbers).forEach { found += Citation(book, NumeralSystem.toLatin(it.value).toInt()) }
                    current = null
                }
            }
        }
        return found.distinct()
    }
}
