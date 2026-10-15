package com.zikra.tooling.content

import java.io.File
import java.security.PrivateKey
import java.security.PublicKey
import java.sql.DriverManager
import java.sql.Statement
import java.time.Instant
import java.util.UUID

/** Signing keys and the version stamped on a pack. */
class PackKeys(val privateKey: PrivateKey, val publicKey: PublicKey)

private data class PackSpec(val area: String, val language: String, val edition: String, val version: String)

/** End-to-end operations used by the CLI and by tests. */
class Pipeline(private val sourcesDir: File) {
    private val lock = SourceLock.load(sourcesDir)

    fun buildQuranText(outDir: File, keys: PackKeys, packVersion: String): File {
        val source = lock.source(QURAN_TEXT)
        val text = parseTanzilText(lock.verifiedFile(sourcesDir, QURAN_TEXT))
        val meta = parseQuranMetadata(lock.verifiedFile(sourcesDir, QURAN_METADATA))
        QuranValidation.checkMetadata(meta)
        QuranValidation.checkAyahSequence(
            positions = text.ayahs.map { it.surah to it.ayah },
            texts = text.ayahs.map { it.text },
            what = "Quran text",
        )
        val db = File(outDir, PACK_FILE)
        PackWriter.writeQuranText(db, QURAN_EDITION, text, meta, source)
        return seal(outDir, db, PackSpec(AREA_TEXT, "ar", QURAN_EDITION, packVersion), source, keys)
    }

    fun buildEnglishSaheeh(outDir: File, keys: PackKeys, packVersion: String): File {
        val source = lock.source(EN_SAHEEH)
        val ayahs = parseQuranEnc(lock.verifiedFile(sourcesDir, EN_SAHEEH), source.version)
        QuranValidation.checkAyahSequence(
            positions = ayahs.map { it.surah to it.ayah },
            texts = ayahs.map { it.text },
            what = "Translation",
        )
        val db = File(outDir, PACK_FILE)
        PackWriter.writeTranslation(db, EN_SAHEEH_EDITION, "en", ayahs, source)
        return seal(outDir, db, PackSpec(AREA_TRANSLATION, "en", EN_SAHEEH_EDITION, packVersion), source, keys)
    }

    /**
     * Builds places.sqlite. It is bundled in the APK (protected by the APK signature), so it is not a
     * downloadable signed pack; the sources are still pinned and verified byte for byte.
     */
    fun buildPlaces(out: File): Places {
        val places = Places.assemble(
            cities = parseGeoCities(lock.verifiedFile(sourcesDir, GEO_CITIES)),
            countries = parseGeoCountries(lock.verifiedFile(sourcesDir, GEO_COUNTRIES)),
            admin1 = parseGeoAdmin1(lock.verifiedFile(sourcesDir, GEO_ADMIN1)),
            names = parseGeoNames(lock.verifiedFile(sourcesDir, GEO_NAMES)),
        )
        PackWriter.writePlaces(out, places, lock.source(GEO_CITIES))
        return places
    }

    /** Regenerates the pinned names file from upstream alternateNamesV2.txt (run when updating GeoNames). */
    fun extractPlaceNames(alternateNames: File) {
        val ids = HashSet<Long>()
        parseGeoCities(lock.verifiedFile(sourcesDir, GEO_CITIES)).mapTo(ids) { it.id }
        parseGeoCountries(lock.verifiedFile(sourcesDir, GEO_COUNTRIES)).mapTo(ids) { it.geonameId }
        parseGeoAdmin1(lock.verifiedFile(sourcesDir, GEO_ADMIN1)).mapTo(ids) { it.geonameId }
        PlacesExtract.run(alternateNames, ids, File(sourcesDir, lock.source(GEO_NAMES).path))
    }

    private fun seal(outDir: File, db: File, spec: PackSpec, source: PinnedSource, keys: PackKeys): File {
        val manifest = Manifest(
            packId = UUID.randomUUID().toString(),
            area = spec.area,
            language = spec.language,
            editionCode = spec.edition,
            version = spec.version,
            minAppVersion = MIN_APP_VERSION,
            createdAt = Instant.now().toString(),
            source = PackSource(source.publisher, source.license, source.url, source.version, source.sha256),
            files = listOf(PackFile(db.name, db.length(), sha256Hex(db.readBytes()))),
            verification = PackVerification(
                surahCount = QuranValidation.SURAH_COUNT,
                ayahCount = QuranValidation.AYAH_COUNT,
                perSurahAyahCountsSha256 = QuranValidation.perSurahCountsSha256(),
            ),
        )
        val out = File(outDir, MANIFEST_FILE)
        out.writeText(Signing.encode(Signing.sign(manifest, keys.privateKey, keys.publicKey)))
        return out
    }

    companion object {
        const val QURAN_TEXT = "tanzil-quran-uthmani"
        const val QURAN_METADATA = "tanzil-quran-metadata"
        const val EN_SAHEEH = "quranenc-english-saheeh"
        const val GEO_CITIES = "geonames-cities15000"
        const val GEO_COUNTRIES = "geonames-country-info"
        const val GEO_ADMIN1 = "geonames-admin1"
        const val GEO_NAMES = "geonames-names-waves"
        const val QURAN_EDITION = "ar.hafs.uthmani.tanzil"
        const val EN_SAHEEH_EDITION = "en.saheeh.quranenc"
        const val MIN_APP_VERSION = "0.1.0"
        const val PACK_FILE = "pack.sqlite"
        const val MANIFEST_FILE = "manifest.signed.json"
        private const val AREA_TEXT = "quran_text"
        private const val AREA_TRANSLATION = "quran_translation"

        /**
         * Verifies a built pack exactly as the app will (CONTENT_PACKS_AR.md §3): signature, file size and
         * SHA-256, SQLite integrity, and the Quran structure. Throws on the first failure.
         */
        fun verify(packDir: File, publicKey: PublicKey): Manifest {
            val manifest = Signing.open(Signing.decode(File(packDir, MANIFEST_FILE).readText()), publicKey)
            manifest.files.forEach { checkFile(packDir, it) }
            if (manifest.verification.perSurahAyahCountsSha256 != QuranValidation.perSurahCountsSha256()) {
                throw PipelineException("Unexpected per-surah structure")
            }
            checkDatabase(File(packDir, manifest.files.first().path), manifest.area)
            return manifest
        }

        private fun checkFile(packDir: File, f: PackFile) {
            val file = File(packDir, f.path)
            ensure(file.isFile) { "Missing pack file ${f.path}" }
            ensure(file.length() == f.sizeBytes) { "Size mismatch for ${f.path}" }
            ensure(sha256Hex(file.readBytes()) == f.sha256) { "SHA-256 mismatch for ${f.path}" }
        }

        private fun checkDatabase(db: File, area: String) {
            val table = if (area == AREA_TEXT) "quran_ayah_text" else "quran_translation"
            DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
                c.createStatement().use { st ->
                    if (st.firstString("PRAGMA integrity_check") != "ok") {
                        throw PipelineException("SQLite integrity check failed")
                    }
                    val ayahs = st.firstString("SELECT COUNT(*) FROM $table")?.toInt()
                    val surahs = st.firstString("SELECT COUNT(DISTINCT surah) FROM $table")?.toInt()
                    if (ayahs != QuranValidation.AYAH_COUNT || surahs != QuranValidation.SURAH_COUNT) {
                        throw PipelineException("Pack holds $ayahs ayahs in $surahs surahs")
                    }
                }
            }
        }

        private fun Statement.firstString(sql: String): String? =
            executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null }
    }
}
