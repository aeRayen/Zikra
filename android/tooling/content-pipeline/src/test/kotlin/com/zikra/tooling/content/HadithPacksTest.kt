package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.sql.DriverManager

/** WP 3.4a (ADR-046): hadith packs - built from the pinned download, signed, refused when altered. */
class HadithPacksTest {

    @get:Rule
    val tmp = TemporaryFolder()

    // The shape of fawazahmed0/hadith-api, with placeholder words (no hadith text in code).
    private val book = """
        {
          "metadata": { "name": "Sunan Abu Dawud", "sections": { "0": "", "1": "Purification", "2": "Prayer" } },
          "hadiths": [
            { "hadithnumber": 1, "arabicnumber": 1, "text": "نص أول", "grades": [
                { "name": "Al-Albani", "grade": "Sahih" }, { "name": "Zubair Ali Zai", "grade": "Hasan" } ],
              "reference": { "book": 1, "hadith": 1 } },
            { "hadithnumber": 2, "text": "", "grades": [], "reference": { "book": 1, "hadith": 2 } },
            { "hadithnumber": 3, "arabicnumber": "2.01", "text": "نص ثان", "grades": [],
              "reference": { "book": 2, "hadith": 1 } }
          ]
        }
    """.trimIndent()

    private fun bookFile(text: String = book): File = File(tmp.root, "ara-abudawud.json").apply { writeText(text) }

    @Test
    fun parsingKeepsTheTextAndEveryGradingWithItsScholarAndSkipsPlaceholders() {
        val data = HadithPacks.parse(bookFile(), HadithBook.ABU_DAWUD)
        assertEquals(listOf("1", "2.01"), data.hadiths.map { it.number })
        assertEquals(listOf("Al-Albani" to "Sahih", "Zubair Ali Zai" to "Hasan"), data.hadiths.first().grades)
        assertEquals(mapOf(1 to "Purification", 2 to "Prayer"), data.sections)
        assertEquals(2, data.hadiths.last().section)
    }

    private fun keys(): PackKeys {
        val private = File(tmp.root, "k.private")
        val public = File(tmp.root, "k.public")
        Signing.generateKeyPair(private, public)
        return PackKeys(Signing.loadPrivate(private), Signing.loadPublic(public))
    }

    private fun cited(file: File) = CitedHadiths(
        upstream = HadithBook.entries.map { b ->
            val sha = if (b == HadithBook.ABU_DAWUD) sha256Hex(file.readBytes()) else ""
            UpstreamFile(b.key, "https://example.invalid/${b.key}", sha)
        },
        hadiths = emptyList(),
    )

    @Test
    fun aDownloadThatIsNotThePinnedFileIsRefused() {
        val file = bookFile()
        val cited = cited(file)
        file.appendText(" ")
        assertThrows(PipelineException::class.java) {
            HadithPacks.build(cited, tmp.root, File(tmp.root, "out"), keys(), "1")
        }
    }

    @Test
    fun aPackIsSignedAndUnpacksToExactlyTheHadithsItNames() {
        val file = bookFile()
        val keys = keys()
        val out = File(tmp.root, "out")
        // Only Abu Dawud is present here: build it alone through the same steps.
        val data = HadithPacks.parse(file, HadithBook.ABU_DAWUD)
        val db = File(out.apply { mkdirs() }, HadithPacks.assetName(HadithBook.ABU_DAWUD))
        HadithPackWriter.write(db, data, cited(file).upstream.first { it.book == "abudawud" })
        val verification = PackVerification(itemCount = 2, itemKeysSha256 = HadithPacks.keysSha256(listOf("1", "2.01")))
        HadithPacks.check(db, verification)
        DriverManager.getConnection("jdbc:sqlite:${db.absolutePath}").use { c ->
            c.createStatement().use { st ->
                st.executeQuery("SELECT COUNT(*) FROM hadith_grade WHERE number = '1'").use {
                    it.next()
                    assertEquals(2, it.getInt(1))
                }
            }
        }
        // Signed manifest: opens with the right key only.
        val manifest = Manifest(
            packId = "p",
            area = Pipeline.AREA_HADITH,
            language = "ar",
            editionCode = HadithPacks.edition(HadithBook.ABU_DAWUD),
            version = "1", minAppVersion = "0.1.0", createdAt = "now",
            source = PackSource("s", "l", "u", "v", "x"), files = emptyList(), verification = verification,
        )
        val signed = Signing.sign(manifest, keys.privateKey, keys.publicKey)
        assertEquals(manifest, Signing.open(signed, keys.publicKey))
        assertThrows(PipelineException::class.java) { Signing.open(signed, keys().publicKey) }
    }

    @Test
    fun aPackWithAMissingOrChangedHadithIsRefused() {
        val file = bookFile()
        val db = File(tmp.root, "p.sqlite")
        val upstream = cited(file).upstream.first { it.book == "abudawud" }
        HadithPackWriter.write(db, HadithPacks.parse(file, HadithBook.ABU_DAWUD), upstream)
        val right = PackVerification(itemCount = 2, itemKeysSha256 = HadithPacks.keysSha256(listOf("1", "2.01")))
        assertThrows(PipelineException::class.java) { HadithPacks.check(db, right.copy(itemCount = 3)) }
        assertThrows(PipelineException::class.java) {
            HadithPacks.check(db, right.copy(itemKeysSha256 = HadithPacks.keysSha256(listOf("1", "2"))))
        }
    }

    @Test
    fun aCompressedDownloadIsCheckedBeforeAndAfterUnpacking() {
        val file = bookFile()
        val db = File(tmp.root, "p.sqlite")
        val upstream = cited(file).upstream.first { it.book == "abudawud" }
        HadithPackWriter.write(db, HadithPacks.parse(file, HadithBook.ABU_DAWUD), upstream)
        val gz = File(tmp.root, "p.sqlite.gz")
        java.util.zip.GZIPOutputStream(gz.outputStream()).use { out -> db.inputStream().use { it.copyTo(out) } }
        val entry = PackFile(
            gz.name,
            gz.length(),
            sha256Hex(gz.readBytes()),
            HadithPacks.GZIP,
            db.length(),
            sha256Hex(db.readBytes()),
        )
        val unpacked = File(tmp.root, "u.sqlite")
        HadithPacks.unpack(gz, entry, unpacked)
        assertTrue(unpacked.readBytes().contentEquals(db.readBytes()))
        assertThrows(PipelineException::class.java) {
            HadithPacks.unpack(gz, entry.copy(uncompressedSha256 = "0".repeat(64)), File(tmp.root, "x.sqlite"))
        }
    }
}
