package com.zikra.core.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.zikra.core.i18n.SearchNormalizer
import java.io.Closeable

/** Who made a translation and under which terms; shown wherever the translation is (WP 2.13). */
data class TranslationInfo(
    val edition: String,
    /** BCP 47 base language, e.g. "en". */
    val language: String,
    val title: String,
    val publisher: String,
    val version: String,
    val license: String,
    val sourceUrl: String,
)

/** A translated ayah, verbatim from the publisher (QuranEnc terms: no modification). */
data class TranslatedAyah(val surah: Int, val ayah: Int, val text: String, val footnotes: String?)

/**
 * A translation of the meanings bundled in the APK (WP 2.13a). It is always shown NEXT TO the Arabic
 * text and never replaces it. Opening checks that all 6236 ayahs are present. Disk I/O: off the main thread.
 */
class QuranTranslation private constructor(private val db: SQLiteDatabase) : Closeable {

    val info: TranslationInfo = readInfo()

    fun surah(surah: Int): Map<Int, TranslatedAyah> =
        db.rawQuery(
            "SELECT surah, ayah, text, footnotes FROM quran_translation WHERE edition = ? AND surah = ? ORDER BY ayah",
            arrayOf(info.edition, surah.toString()),
        ).use { c ->
            buildMap {
                while (c.moveToNext()) c.translated().let { put(it.ayah, it) }
            }
        }

    /** Ayahs whose translation contains [query] (case, accents and marks ignored), in mushaf order. */
    fun search(query: String, limit: Int = QuranText.SEARCH_LIMIT): List<TranslatedAyah> {
        val key = SearchNormalizer.normalize(query)
        if (key.length < MIN_QUERY_LENGTH) return emptyList()
        val escaped = key.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        return db.rawQuery(
            """
            SELECT surah, ayah, text, footnotes FROM quran_translation
            WHERE edition = ? AND search_text LIKE ? ESCAPE '!' ORDER BY surah, ayah LIMIT $limit
            """,
            arrayOf(info.edition, "%$escaped%"),
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(c.translated())
            }
        }
    }

    override fun close() = db.close()

    private fun readInfo(): TranslationInfo {
        val values = db.rawQuery("SELECT key, value FROM pack_info", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        return TranslationInfo(
            edition = values["edition"].orEmpty(),
            language = values["language"].orEmpty(),
            title = values["title"] ?: values["edition"].orEmpty(),
            publisher = values["source_publisher"].orEmpty(),
            version = values["source_version"].orEmpty(),
            license = values["license"].orEmpty(),
            sourceUrl = values["source_url"].orEmpty(),
        )
    }

    companion object {
        /** The bundled translation (Saheeh International, English, via QuranEnc). */
        const val ENGLISH_ASSET = "translation-en.sqlite"
        private const val MIN_QUERY_LENGTH = 2

        fun openEnglish(context: Context): QuranTranslation {
            val (db, target) = BundledDatabase.open(context, ENGLISH_ASSET, "translations")
            val translation = QuranTranslation(db)
            val count = db.rawQuery(
                "SELECT COUNT(*) FROM quran_translation WHERE edition = ?",
                arrayOf(translation.info.edition)
            )
                .use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }
            if (count != QuranText.AYAH_COUNT) {
                translation.close()
                target.delete()
                throw QuranIntegrityException("Bundled translation has $count ayahs")
            }
            return translation
        }
    }
}

private fun android.database.Cursor.translated(): TranslatedAyah {
    val footnotes = getColumnIndexOrThrow("footnotes")
    return TranslatedAyah(
        surah = getInt(getColumnIndexOrThrow("surah")),
        ayah = getInt(getColumnIndexOrThrow("ayah")),
        text = getString(getColumnIndexOrThrow("text")),
        footnotes = if (isNull(footnotes)) null else getString(footnotes),
    )
}
