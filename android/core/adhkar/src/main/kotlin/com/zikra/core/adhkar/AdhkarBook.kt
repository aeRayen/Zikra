package com.zikra.core.adhkar

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zikra.core.quran.BundledDatabase
import java.io.Closeable

data class AdhkarChapter(val id: Int, val title: String, val size: Int)

/** A piece of a dhikr as displayed. */
sealed interface DhikrPart {
    /** The dhikr's own words, verbatim from the source. */
    data class Words(val text: String) : DhikrPart

    /** A quoted ayah (or the quoted part of it), in the verified Tanzil Uthmani text. */
    data class Quran(val surah: Int, val ayah: Int, val text: String) : DhikrPart
}

data class Dhikr(
    val id: Int,
    val chapter: Int,
    /** How many times it is said (1 when the source gives no number). */
    val repeat: Int,
    val parts: List<DhikrPart>,
    /** The author's footnotes, verbatim (each starts with its marker, e.g. "(١)"). */
    val references: List<String>,
)

/** Where the adhkar come from; shown on the adhkar screens (ADR-040). */
data class AdhkarSourceInfo(val textUrl: String, val referencesUrl: String, val quranSource: String)

/**
 * Hisn al-Muslim bundled in the APK (WP 2.14b): 132 chapters, 267 adhkar, every one with its verified
 * references. Opening checks the counts. Disk I/O: off the main thread.
 */
class AdhkarBook private constructor(private val db: SQLiteDatabase) : Closeable {

    val info: AdhkarSourceInfo = readInfo()

    fun chapters(): List<AdhkarChapter> =
        db.rawQuery(
            """
            SELECT c.id, c.title, COUNT(d.id) FROM adhkar_chapter c JOIN dhikr d ON d.chapter = c.id
            GROUP BY c.id ORDER BY c.id
            """,
            null,
        ).use { c -> buildList { while (c.moveToNext()) add(AdhkarChapter(c.getInt(0), c.getString(1), c.getInt(2))) } }

    fun chapter(id: Int): List<Dhikr> {
        val parts = db.rawQuery(
            """
            SELECT p.dhikr, p.text, p.surah, p.ayah FROM dhikr_part p JOIN dhikr d ON d.id = p.dhikr
            WHERE d.chapter = ? ORDER BY p.dhikr, p.ord
            """,
            arrayOf(id.toString()),
        ).use { c -> c.groupByDhikr { it.part() } }
        val references = db.rawQuery(
            """
            SELECT r.dhikr, r.text FROM dhikr_reference r JOIN dhikr d ON d.id = r.dhikr
            WHERE d.chapter = ? ORDER BY r.dhikr, r.ord
            """,
            arrayOf(id.toString()),
        ).use { c -> c.groupByDhikr { it.getString(1) } }
        return db.rawQuery("SELECT id, repeat FROM dhikr WHERE chapter = ? ORDER BY id", arrayOf(id.toString()))
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val dhikr = c.getInt(0)
                        add(Dhikr(dhikr, id, c.getInt(1), parts[dhikr].orEmpty(), references[dhikr].orEmpty()))
                    }
                }
            }
    }

    override fun close() = db.close()

    private fun readInfo(): AdhkarSourceInfo {
        val values = db.rawQuery("SELECT key, value FROM pack_info", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        return AdhkarSourceInfo(
            textUrl = values["source_url"].orEmpty(),
            referencesUrl = values["references_source_url"].orEmpty(),
            quranSource = values["quran_source_id"].orEmpty(),
        )
    }

    private fun count(table: String) =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    companion object {
        const val ASSET = "adhkar.sqlite"
        const val DHIKR_COUNT = 267
        const val CHAPTER_COUNT = 132

        /** Daily chapters, offered first (numbers of the book, checked against their titles by tests). */
        const val WAKING = 1
        const val AFTER_PRAYER = 25
        const val MORNING_EVENING = 27
        const val SLEEP = 28
        val DAILY = listOf(MORNING_EVENING, AFTER_PRAYER, SLEEP, WAKING)

        fun open(context: Context): AdhkarBook {
            val (db, target) = BundledDatabase.open(context, ASSET, "adhkar")
            val book = AdhkarBook(db)
            if (book.count("dhikr") != DHIKR_COUNT || book.count("adhkar_chapter") != CHAPTER_COUNT) {
                book.close()
                target.delete()
                throw AdhkarIntegrityException("Bundled adhkar are incomplete")
            }
            return book
        }
    }
}

class AdhkarIntegrityException(message: String) : IllegalStateException(message)

private fun Cursor.part(): DhikrPart = when {
    isNull(SURAH) -> DhikrPart.Words(getString(TEXT))
    else -> DhikrPart.Quran(getInt(SURAH), getInt(AYAH), getString(TEXT))
}

// Columns of the dhikr_part query: dhikr, text, surah, ayah.
private const val TEXT = 1
private const val SURAH = 2
private const val AYAH = 3

private fun <T> Cursor.groupByDhikr(value: (Cursor) -> T): Map<Int, List<T>> {
    val result = linkedMapOf<Int, MutableList<T>>()
    while (moveToNext()) result.getOrPut(getInt(0)) { mutableListOf() } += value(this)
    return result
}
