package com.zikra.core.hadith

import android.database.sqlite.SQLiteDatabase
import com.zikra.core.i18n.SearchNormalizer
import java.io.Closeable
import java.io.File

/** A chapter ("book") of a hadith collection. Titles exist only in English in the source (ADR-046). */
data class HadithSection(val id: Int, val titleEnglish: String, val size: Int)

/** One grading, verbatim: who graded it and how. */
data class HadithGrade(val scholar: String, val grade: String)

data class Hadith(
    val book: HadithBook,
    val number: String,
    val section: Int,
    val text: String,
    val grades: List<HadithGrade>,
)

/** Reads one installed (verified) hadith pack. Disk I/O: off the main thread. */
class HadithReader private constructor(val book: HadithBook, private val db: SQLiteDatabase) : Closeable {

    fun sections(): List<HadithSection> = db.rawQuery(
        """
        SELECT s.id, s.title_en, COUNT(h.number) FROM hadith_section s JOIN hadith h ON h.section = s.id
        GROUP BY s.id ORDER BY s.id
        """,
        null,
    ).use { c -> buildList { while (c.moveToNext()) add(HadithSection(c.getInt(0), c.getString(1), c.getInt(2))) } }

    fun section(id: Int): List<Hadith> =
        hadiths("$SELECT WHERE section = ? ORDER BY number_value", arrayOf(id.toString()))

    /** Hadiths whose text contains [query] (tashkeel and hamza forms ignored), in book order. */
    fun search(query: String, limit: Int = SEARCH_LIMIT): List<Hadith> {
        val key = SearchNormalizer.normalize(query)
        if (key.length < MIN_QUERY) return emptyList()
        val escaped = key.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return hadiths(
            "$SELECT WHERE search_text LIKE ? ESCAPE '!' ORDER BY number_value LIMIT $limit",
            arrayOf("%$escaped%"),
        )
    }

    private fun hadiths(sql: String, args: Array<String>): List<Hadith> {
        val rows = db.rawQuery(sql, args).use { c ->
            buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getInt(1), c.getString(2))) }
        }
        if (rows.isEmpty()) return emptyList()
        val grades = grades(rows.map { it.first })
        return rows.map { (number, section, text) -> Hadith(book, number, section, text, grades[number].orEmpty()) }
    }

    private fun grades(numbers: List<String>): Map<String, List<HadithGrade>> =
        numbers.chunked(MAX_ARGS).flatMap { chunk ->
            val marks = chunk.joinToString(",") { "?" }
            db.rawQuery(
                "SELECT number, scholar, grade FROM hadith_grade WHERE number IN ($marks) ORDER BY number, ord",
                chunk.toTypedArray(),
            ).use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0) to HadithGrade(c.getString(1), c.getString(2))) }
            }
        }.groupBy({ it.first }, { it.second })

    override fun close() = db.close()

    companion object {
        const val SEARCH_LIMIT = 100
        private const val SELECT = "SELECT number, section, text FROM hadith"
        private const val MIN_QUERY = 2

        /** SQLite's default limit on «?» parameters is 999. */
        private const val MAX_ARGS = 900

        fun open(book: HadithBook, file: File): HadithReader =
            HadithReader(book, SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY))
    }
}
