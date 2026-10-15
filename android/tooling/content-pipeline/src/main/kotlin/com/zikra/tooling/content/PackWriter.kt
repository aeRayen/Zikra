package com.zikra.tooling.content

import com.zikra.core.i18n.SearchNormalizer
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Writes content packs as SQLite files (CONTENT_PACKS_AR.md §1). Displayed text is inserted exactly
 * as read from the pinned source; only the separate search_text column is normalized.
 */
object PackWriter {

    fun writeQuranText(out: File, edition: String, text: TanzilText, meta: QuranMetadata, source: PinnedSource) {
        connect(out) { db ->
            db.exec(
                """
                CREATE TABLE pack_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
                CREATE TABLE quran_surah (
                  surah INTEGER PRIMARY KEY NOT NULL, ayah_count INTEGER NOT NULL,
                  name_ar TEXT NOT NULL, name_translit TEXT NOT NULL, name_en TEXT NOT NULL,
                  revelation_place TEXT NOT NULL, revelation_order INTEGER NOT NULL);
                CREATE TABLE quran_ayah_meta (
                  surah INTEGER NOT NULL, ayah INTEGER NOT NULL, juz INTEGER NOT NULL,
                  hizb_quarter INTEGER NOT NULL, page INTEGER NOT NULL, sajda TEXT,
                  PRIMARY KEY (surah, ayah));
                CREATE TABLE quran_ayah_text (
                  edition TEXT NOT NULL, surah INTEGER NOT NULL, ayah INTEGER NOT NULL,
                  text TEXT NOT NULL, search_text TEXT NOT NULL, PRIMARY KEY (edition, surah, ayah));
                """,
            )
            writeInfo(db, source, mapOf("edition" to edition, "license_notice" to text.licenseNotice))
            db.batch("INSERT INTO quran_surah VALUES (?,?,?,?,?,?,?)", meta.surahs) { s ->
                listOf(
                    s.number,
                    s.ayahCount,
                    s.nameArabic,
                    s.nameTransliterated,
                    s.nameEnglish,
                    s.revelationPlace,
                    s.revelationOrder,
                )
            }
            val positions = AyahPositions(meta)
            db.batch("INSERT INTO quran_ayah_meta VALUES (?,?,?,?,?,?)", text.ayahs) { a ->
                listOf(
                    a.surah,
                    a.ayah,
                    positions.juz(a),
                    positions.quarter(a),
                    positions.page(a),
                    positions.sajda(a),
                )
            }
            db.batch("INSERT INTO quran_ayah_text VALUES (?,?,?,?,?)", text.ayahs) { a ->
                listOf(edition, a.surah, a.ayah, a.text, SearchNormalizer.normalize(a.text))
            }
        }
    }

    fun writeTranslation(
        out: File,
        edition: String,
        language: String,
        ayahs: List<TranslatedAyah>,
        source: PinnedSource,
    ) {
        connect(out) { db ->
            db.exec(
                """
                CREATE TABLE pack_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
                CREATE TABLE quran_translation (
                  edition TEXT NOT NULL, surah INTEGER NOT NULL, ayah INTEGER NOT NULL,
                  text TEXT NOT NULL, footnotes TEXT, search_text TEXT NOT NULL,
                  PRIMARY KEY (edition, surah, ayah));
                """,
            )
            writeInfo(db, source, mapOf("edition" to edition, "language" to language))
            db.batch("INSERT INTO quran_translation VALUES (?,?,?,?,?,?)", ayahs) { a ->
                listOf(edition, a.surah, a.ayah, a.text, a.footnotes, SearchNormalizer.normalize(a.text))
            }
        }
    }

    /**
     * places.sqlite (WP 2.1): cities with coordinates and IANA time zone, display names per language,
     * and a prefix-search index of normalized names (every word start of every name, any language).
     */
    fun writePlaces(out: File, places: Places, source: PinnedSource) {
        connect(out) { db ->
            db.exec(
                """
                CREATE TABLE pack_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL);
                CREATE TABLE country (code TEXT PRIMARY KEY NOT NULL, geoname_id INTEGER NOT NULL, name TEXT NOT NULL);
                CREATE TABLE region (code TEXT PRIMARY KEY NOT NULL, geoname_id INTEGER NOT NULL, name TEXT NOT NULL);
                CREATE TABLE city (
                  id INTEGER PRIMARY KEY NOT NULL, name TEXT NOT NULL, country TEXT NOT NULL, region TEXT,
                  latitude REAL NOT NULL, longitude REAL NOT NULL, timezone TEXT NOT NULL, population INTEGER NOT NULL);
                CREATE INDEX city_lat ON city (latitude);
                CREATE TABLE place_name (
                  geoname_id INTEGER NOT NULL, language TEXT NOT NULL, name TEXT NOT NULL,
                  PRIMARY KEY (geoname_id, language)) WITHOUT ROWID;
                CREATE TABLE city_search (
                  key TEXT NOT NULL, city_id INTEGER NOT NULL, PRIMARY KEY (key, city_id)) WITHOUT ROWID;
                """,
            )
            writeInfo(db, source, mapOf("languages" to PLACE_NAME_LANGUAGES.joinToString(",")))
            db.batch("INSERT INTO country VALUES (?,?,?)", places.countries) { listOf(it.code, it.geonameId, it.name) }
            db.batch("INSERT INTO region VALUES (?,?,?)", places.regions) { listOf(it.code, it.geonameId, it.name) }
            db.batch("INSERT INTO city VALUES (?,?,?,?,?,?,?,?)", places.cities) { c ->
                listOf(c.id, c.name, c.country, places.regionCode(c), c.latitude, c.longitude, c.timezone, c.population)
            }
            db.batch("INSERT INTO place_name VALUES (?,?,?)", places.displayNames) {
                listOf(it.geonameId, it.language, it.name)
            }
            db.batch("INSERT INTO city_search VALUES (?,?)", places.searchKeys) { listOf(it.first, it.second) }
        }
    }

    private fun writeInfo(db: Connection, source: PinnedSource, extra: Map<String, String>) {
        val info = mapOf(
            "source_id" to source.id,
            "source_publisher" to source.publisher,
            "source_url" to source.url,
            "source_version" to source.version,
            "source_sha256" to source.sha256,
            "license" to source.license,
        ) + extra
        db.batch("INSERT INTO pack_info VALUES (?,?)", info.entries.toList()) { listOf(it.key, it.value) }
    }

    private fun connect(out: File, block: (Connection) -> Unit) {
        if (out.exists() && !out.delete()) throw PipelineException("Cannot replace ${out.path}")
        out.parentFile?.mkdirs()
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { db ->
            db.autoCommit = false
            block(db)
            db.commit()
            db.autoCommit = true // VACUUM cannot run inside a transaction
            db.createStatement().use { it.execute("VACUUM") }
        }
    }

    private fun Connection.exec(sql: String) = createStatement().use { st ->
        sql.split(';').map { it.trim() }.filter { it.isNotEmpty() }.forEach { st.execute(it) }
    }

    private fun <T> Connection.batch(sql: String, rows: List<T>, values: (T) -> List<Any?>) {
        prepareStatement(sql).use { ps ->
            rows.forEach { row ->
                values(row).forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }
}

/** Maps each ayah to its juz, hizb quarter, page and sajda using the start markers. */
private class AyahPositions(private val meta: QuranMetadata) {
    private fun Marker.key() = surah * KEY_STRIDE + ayah
    private val juz = meta.juzStarts.map { it.key() to it.index }
    private val quarters = meta.quarterStarts.map { it.key() to it.index }
    private val pages = meta.pageStarts.map { it.key() to it.index }
    private val sajdas = meta.sajdas.associate { (m, type) -> m.key() to type }

    private fun Ayah.key() = surah * KEY_STRIDE + ayah
    private fun List<Pair<Int, Int>>.indexFor(key: Int) = last { it.first <= key }.second

    fun juz(a: Ayah) = juz.indexFor(a.key())
    fun quarter(a: Ayah) = quarters.indexFor(a.key())
    fun page(a: Ayah) = pages.indexFor(a.key())
    fun sajda(a: Ayah): String? = sajdas[a.key()]

    private companion object {
        const val KEY_STRIDE = 1000
    }
}
