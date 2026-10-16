package com.zikra.tooling.content

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

/** One hadith as published: its number in the book (e.g. "2711" or "2711.02" for a variant chain). */
data class HadithRecord(
    val number: String,
    val section: Int,
    val text: String,
    /** (scholar, grading) in the source's order; empty for al-Bukhari and Muslim. */
    val grades: List<Pair<String, String>>,
) {
    val numberValue: Double get() = number.toDouble()
}

data class HadithBookData(
    val book: HadithBook,
    val name: String,
    val sections: Map<Int, String>,
    val hadiths: List<HadithRecord>,
)

/**
 * The six books as signed downloadable packs (WP 3.4a, ADR-046): the Arabic text and every grading
 * with the name of the scholar who gave it, verbatim from fawazahmed0/hadith-api (Unlicense). Each
 * downloaded book must be byte-identical to the file whose SHA-256 is pinned (hadith-cited.json).
 */
object HadithPacks {
    const val EDITION_PREFIX = "ar."
    const val EDITION_SUFFIX = ".hadith-api"

    fun edition(book: HadithBook) = EDITION_PREFIX + book.key + EDITION_SUFFIX

    fun assetName(book: HadithBook) = "hadith-${book.key}.sqlite"

    /** What is published and downloaded: the database, gzip-compressed (about a fifth of its size). */
    fun downloadName(book: HadithBook) = assetName(book) + ".gz"

    fun manifestName(book: HadithBook) = "hadith-${book.key}.manifest.json"

    fun parse(file: File, book: HadithBook): HadithBookData {
        val root = Json.parseToJsonElement(file.readText(Charsets.UTF_8)).jsonObject
        val meta = root.getValue("metadata").jsonObject
        val sections = meta.getValue("sections").jsonObject.entries
            .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v.jsonPrimitive.content.trim() } }
            .filter { it.second.isNotEmpty() }
            .toMap()
        val hadiths = root.getValue("hadiths").jsonArray.mapNotNull { element ->
            val h = element.jsonObject
            val number = h["arabicnumber"]?.jsonPrimitive?.content.orEmpty()
            val text = h["text"]?.jsonPrimitive?.content.orEmpty()
            // A few records are empty placeholders (no number, no text): not hadiths.
            if (number.isEmpty() || text.isBlank()) return@mapNotNull null
            HadithRecord(number, section(h), text, grades(h))
        }
        return HadithBookData(book, meta.getValue("name").jsonPrimitive.content, sections, hadiths)
    }

    /** Hash of the hadith numbers in order: the app checks the downloaded book has exactly these. */
    fun keysSha256(numbers: List<String>): String = sha256Hex(numbers.joinToString("\n").toByteArray(Charsets.UTF_8))

    /** Builds, checks and signs one pack per book into [outDir], from the pinned downloads in [booksDir]. */
    fun build(cited: CitedHadiths, booksDir: File, outDir: File, keys: PackKeys, version: String): List<Manifest> {
        outDir.mkdirs()
        return HadithBook.entries.map { book ->
            val upstream = cited.upstream.first { it.book == book.key }
            val file = File(booksDir, "ara-${book.key}.json")
            ensure(file.isFile) { "Missing ${file.path} (download ${upstream.url})" }
            ensure(sha256Hex(file.readBytes()) == upstream.sha256) {
                "${file.name} differs from the pinned download (${upstream.sha256})"
            }
            val data = parse(file, book)
            val numbers = data.hadiths.map { it.number }
            ensure(numbers.distinct().size == numbers.size) { "Duplicate numbers in ${book.key}" }
            val db = File(outDir, assetName(book))
            HadithPackWriter.write(db, data, upstream)
            val verification = PackVerification(
                itemCount = data.hadiths.size,
                itemKeysSha256 = keysSha256(data.hadiths.map { it.number }),
            )
            check(db, verification)
            val manifest = Manifest(
                packId = UUID.randomUUID().toString(),
                area = Pipeline.AREA_HADITH,
                language = "ar",
                editionCode = edition(book),
                version = version,
                minAppVersion = Pipeline.MIN_APP_VERSION,
                createdAt = Instant.now().toString(),
                source = PackSource(data.name, LICENSE, upstream.url, "hadith-api@1", upstream.sha256),
                files = listOf(compressed(db, File(outDir, downloadName(book)))),
                verification = verification,
            )
            val signed = Signing.sign(manifest, keys.privateKey, keys.publicKey)
            File(outDir, manifestName(book)).writeText(Signing.encode(signed))
            manifest
        }
    }

    private fun compressed(db: File, gz: File): PackFile {
        java.util.zip.GZIPOutputStream(gz.outputStream()).use { out -> db.inputStream().use { it.copyTo(out) } }
        return PackFile(
            path = gz.name,
            sizeBytes = gz.length(),
            sha256 = sha256Hex(gz.readBytes()),
            compression = GZIP,
            uncompressedSizeBytes = db.length(),
            uncompressedSha256 = sha256Hex(db.readBytes()),
        )
    }

    /** Unpacks a downloaded gzip file and checks it against the manifest, as the app does. */
    fun unpack(gz: File, file: PackFile, out: File) {
        java.util.zip.GZIPInputStream(gz.inputStream()).use { input -> out.outputStream().use { input.copyTo(it) } }
        ensure(out.length() == file.uncompressedSizeBytes) { "Size mismatch after unpacking ${file.path}" }
        ensure(sha256Hex(out.readBytes()) == file.uncompressedSha256) {
            "SHA-256 mismatch after unpacking ${file.path}"
        }
    }

    const val GZIP = "gzip"
    const val LICENSE = "The Unlicense (public domain)"

    /** A hadith pack is sound when SQLite says so and it holds exactly the hadith numbers the manifest names. */
    fun check(db: File, verification: PackVerification) {
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.executeQuery("PRAGMA integrity_check").use { r ->
                    ensure(r.next() && r.getString(1) == "ok") { "SQLite integrity check failed" }
                }
                val numbers = st.executeQuery("SELECT number FROM hadith ORDER BY rowid").use { r ->
                    buildList { while (r.next()) add(r.getString(1)) }
                }
                ensure(numbers.size == verification.itemCount) {
                    "Expected ${verification.itemCount} hadiths, got ${numbers.size}"
                }
                ensure(keysSha256(numbers) == verification.itemKeysSha256) { "Hadith numbers differ from the manifest" }
            }
        }
    }
}

private fun section(h: JsonObject): Int = h["reference"]?.jsonObject?.get("book")?.jsonPrimitive?.int ?: 0

private fun grades(h: JsonObject): List<Pair<String, String>> =
    h["grades"]?.jsonArray.orEmpty().map { g ->
        val o = g.jsonObject
        o.getValue("name").jsonPrimitive.content.trim() to o.getValue("grade").jsonPrimitive.content.trim()
    }
