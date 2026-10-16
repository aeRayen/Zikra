package com.zikra.core.quran

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import java.io.Closeable

/** A surah as given by the Tanzil metadata. Names are shown as published; never machine-translated. */
data class Surah(
    val number: Int,
    val ayahCount: Int,
    val nameArabic: String,
    val nameTransliterated: String,
    val nameEnglish: String,
    /** "Meccan" or "Medinan" (Tanzil metadata). */
    val revelationPlace: String,
    val revelationOrder: Int,
)

/** One ayah. [text] is the verbatim Tanzil Uthmani text (CONTENT_PACKS_AR.md §4): display it as is. */
data class Ayah(
    val surah: Int,
    val ayah: Int,
    val text: String,
    val juz: Int,
    val hizbQuarter: Int,
    val page: Int,
    /** "recommended" / "obligatory" for sajda verses, otherwise null. */
    val sajda: String?,
)

/** [ayahs]: the first matches in mushaf order; [total]: all matches. */
data class QuranSearchResult(val ayahs: List<Ayah>, val total: Int)

/** Thrown when the bundled text does not have the structure every copy of the Quran must have. */
class QuranIntegrityException(message: String) : IllegalStateException(message)

/**
 * Read-only access to the Quran text bundled in the APK (WP 2.9). Opening checks the structure again
 * (114 surahs, 6236 ayahs, per-surah counts), so a damaged copy is never shown. Disk I/O: call off the
 * main thread.
 */
class QuranText private constructor(internal val db: SQLiteDatabase) : Closeable {

    fun surahs(): List<Surah> =
        db.rawQuery("SELECT * FROM quran_surah ORDER BY surah", null).use { c -> c.list { surah() } }

    fun surah(number: Int): Surah? =
        db.rawQuery("SELECT * FROM quran_surah WHERE surah = ?", arrayOf(number.toString())).use { c ->
            c.list { surah() }.firstOrNull()
        }

    fun ayahs(surah: Int): List<Ayah> = ayahsWhere("t.surah = ?", surah)

    /** Ayahs on a page of the Madani mushaf layout (1..604). */
    fun page(page: Int): List<Ayah> = ayahsWhere("m.page = ?", page)

    fun juz(juz: Int): List<Ayah> = ayahsWhere("m.juz = ?", juz)

    /** Tanzil's licence notice; must be shown with the text (CC BY 3.0, «About this text»). */
    fun licenseNotice(): String = info("license_notice").orEmpty()

    fun sourceVersion(): String = info("source_version").orEmpty()

    override fun close() = db.close()

    private fun info(key: String): String? =
        db.rawQuery("SELECT value FROM pack_info WHERE key = ?", arrayOf(key)).use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    private fun ayahsWhere(condition: String, value: Int): List<Ayah> {
        val sql = """
            SELECT t.surah, t.ayah, t.text, m.juz, m.hizb_quarter, m.page, m.sajda
            FROM quran_ayah_text t JOIN quran_ayah_meta m ON m.surah = t.surah AND m.ayah = t.ayah
            WHERE t.edition = '$EDITION' AND $condition ORDER BY t.surah, t.ayah
        """
        return db.rawQuery(sql, arrayOf(value.toString())).use { c ->
            c.list { ayah() }
        }
    }

    companion object {
        const val ASSET = "quran.sqlite"
        const val SEARCH_LIMIT = 200
        const val SURAH_COUNT = 114
        const val AYAH_COUNT = 6236
        const val PAGE_COUNT = 604
        const val JUZ_COUNT = 30

        /** Edition code written by the content pipeline (Pipeline.QURAN_EDITION). */
        const val EDITION = "ar.hafs.uthmani.tanzil"

        /**
         * Opens the bundled text, copying it out of the APK once per install/update (SQLite cannot read
         * compressed assets). It lives in noBackupFilesDir: app data, restored from the APK, not backed up.
         */
        fun open(context: Context): QuranText {
            val (db, target) = BundledDatabase.open(context, ASSET, "quran")
            val text = QuranText(db)
            try {
                verifyStructure(text.db)
            } catch (e: QuranIntegrityException) {
                text.close()
                target.delete() // copied again from the APK on the next open
                throw e
            }
            return text
        }
    }
}

private fun Cursor.int(column: String) = getInt(getColumnIndexOrThrow(column))

private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))

private fun Cursor.surah() = Surah(
    number = int("surah"),
    ayahCount = int("ayah_count"),
    nameArabic = string("name_ar"),
    nameTransliterated = string("name_translit"),
    nameEnglish = string("name_en"),
    revelationPlace = string("revelation_place"),
    revelationOrder = int("revelation_order"),
)

internal fun Cursor.ayahs(): List<Ayah> = list { ayah() }

private fun Cursor.ayah() = Ayah(
    surah = int("surah"),
    ayah = int("ayah"),
    text = string("text"),
    juz = int("juz"),
    hizbQuarter = int("hizb_quarter"),
    page = int("page"),
    sajda = getColumnIndexOrThrow("sajda").let { if (isNull(it)) null else getString(it) },
)

private fun <T> Cursor.list(row: Cursor.() -> T): List<T> = buildList { while (moveToNext()) add(row()) }

/** Checks the structure every copy of the Quran must have, so a damaged copy is never shown. */
private fun verifyStructure(db: SQLiteDatabase) {
    val ayahs = scalar(db, "SELECT COUNT(*) FROM quran_ayah_text WHERE edition = '${QuranText.EDITION}'")
    val surahs = scalar(db, "SELECT COUNT(DISTINCT surah) FROM quran_ayah_text WHERE edition = '${QuranText.EDITION}'")
    if (ayahs != QuranText.AYAH_COUNT || surahs != QuranText.SURAH_COUNT) {
        throw QuranIntegrityException("Bundled Quran has $ayahs ayahs in $surahs surahs")
    }
    val mismatch = scalar(
        db,
        """
        SELECT COUNT(*) FROM quran_surah s
        WHERE s.ayah_count != (SELECT COUNT(*) FROM quran_ayah_text t WHERE t.surah = s.surah AND t.edition = '${QuranText.EDITION}')
        """,
    )
    if (mismatch != 0) throw QuranIntegrityException("Per-surah ayah counts do not match")
}

private fun scalar(db: SQLiteDatabase, sql: String): Int =
    db.rawQuery(sql, null).use { c -> if (c.moveToFirst()) c.getInt(0) else -1 }
