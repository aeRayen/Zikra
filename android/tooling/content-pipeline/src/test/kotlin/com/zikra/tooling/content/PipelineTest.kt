package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.ClassRule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.security.PrivateKey
import java.security.PublicKey
import java.sql.DriverManager

/**
 * Acceptance for WP 1.15 (BUILD_PLAN): imports the real pinned sources, verifies structure, builds
 * signed packs, and rejects tampered or incomplete input. Uses a throw-away key generated per run.
 */
class PipelineTest {

    companion object {
        @get:ClassRule
        @JvmStatic
        val tmp = TemporaryFolder()

        private lateinit var sources: File
        private lateinit var privateKey: PrivateKey
        private lateinit var publicKey: PublicKey
        private lateinit var quranPack: File

        @BeforeClass
        @JvmStatic
        fun buildOnce() {
            sources = File(requireNotNull(System.getProperty("zikra.contentDir")), "sources")
            val keys = tmp.newFolder("keys")
            Signing.generateKeyPair(File(keys, "private.key"), File(keys, "public.key"))
            privateKey = Signing.loadPrivate(File(keys, "private.key"))
            publicKey = Signing.loadPublic(File(keys, "public.key"))
            quranPack = tmp.newFolder("quran")
            Pipeline(sources).buildQuranText(quranPack, PackKeys(privateKey, publicKey), "2026.10.1")
        }

        private fun query(pack: File, sql: String): List<List<String?>> =
            DriverManager.getConnection("jdbc:sqlite:${File(pack, "pack.sqlite").absolutePath}").use { c ->
                c.createStatement().use { st ->
                    st.executeQuery(sql).use { rs ->
                        val cols = rs.metaData.columnCount
                        buildList { while (rs.next()) add((1..cols).map { rs.getString(it) }) }
                    }
                }
            }
    }

    @Test
    fun builtQuranPackVerifiesAndHasFullStructure() {
        val manifest = Pipeline.verify(quranPack, publicKey)
        assertEquals("quran_text", manifest.area)
        assertEquals("ar.hafs.uthmani.tanzil", manifest.editionCode)
        assertEquals("6236", query(quranPack, "SELECT COUNT(*) FROM quran_ayah_text").single()[0])
        assertEquals("114", query(quranPack, "SELECT COUNT(*) FROM quran_surah").single()[0])
    }

    @Test
    fun displayedTextIsByteIdenticalToTheSource() {
        val source = parseTanzilText(File(sources, "tanzil/quran-uthmani.txt")).ayahs
        val packed = query(quranPack, "SELECT surah, ayah, text FROM quran_ayah_text ORDER BY surah, ayah")
        assertEquals(source.size, packed.size)
        source.zip(packed).forEach { (a, row) ->
            assertEquals("${a.surah}:${a.ayah}", a.text, row[2])
        }
    }

    @Test
    fun searchTextIsNormalizedButSeparate() {
        val (text, search) = query(quranPack, "SELECT text, search_text FROM quran_ayah_text WHERE surah=1 AND ayah=2")
            .single().let { it[0]!! to it[1]!! }
        assertNotEquals(text, search)
        assertTrue("diacritics must be removed from search_text", search.none { it in 'ً'..'ْ' })
    }

    @Test
    fun metadataPositionsMatchKnownMushafBoundaries() {
        fun meta(s: Int, a: Int) = query(quranPack, "SELECT juz, page FROM quran_ayah_meta WHERE surah=$s AND ayah=$a")
            .single()
        assertEquals(listOf("1", "1"), meta(1, 1))
        assertEquals("2", meta(2, 142)[0]) // juz 2 starts at al-Baqarah 142
        assertEquals("30", meta(78, 1)[0]) // juz 30 starts at an-Naba'
        assertEquals("604", meta(114, 6)[1]) // last page
        assertEquals("15", query(quranPack, "SELECT COUNT(*) FROM quran_ayah_meta WHERE sajda IS NOT NULL").single()[0])
        assertEquals("الفاتحة", query(quranPack, "SELECT name_ar FROM quran_surah WHERE surah=1").single()[0])
    }

    @Test
    fun tanzilLicenseNoticeTravelsWithThePack() {
        val notice = query(quranPack, "SELECT value FROM pack_info WHERE key='license_notice'").single()[0]!!
        assertTrue(notice.contains("Tanzil") && notice.contains("tanzil.net"))
    }

    @Test
    fun englishTranslationPackIsComplete() {
        val out = tmp.newFolder("english")
        Pipeline(sources).buildEnglishSaheeh(out, PackKeys(privateKey, publicKey), "1.1.2")
        assertEquals("en.saheeh.quranenc", Pipeline.verify(out, publicKey).editionCode)
        assertEquals("0", query(out, "SELECT COUNT(*) FROM quran_translation WHERE trim(text) = ''").single()[0])
    }

    @Test
    fun modifiedSourceIsRejectedByThePin() {
        val copy = tmp.newFolder("tampered-sources")
        sources.copyRecursively(copy, overwrite = true)
        val text = File(copy, "tanzil/quran-uthmani.txt")
        text.writeText(text.readText().replaceFirst("ٱلْحَمْدُ", "ٱلحَمْدُ"))
        val error = assertThrows(PipelineException::class.java) {
            Pipeline(copy).buildQuranText(tmp.newFolder(), PackKeys(privateKey, publicKey), "x")
        }
        assertTrue(error.message!!.contains("SHA-256 mismatch"))
    }

    @Test
    fun missingAyahFailsStructureCheck() {
        val ayahs = parseTanzilText(
            File(sources, "tanzil/quran-uthmani.txt")
        ).ayahs.filterNot { it.surah == 2 && it.ayah == 255 }
        val error = assertThrows(PipelineException::class.java) {
            QuranValidation.checkAyahSequence(ayahs.map { it.surah to it.ayah }, ayahs.map { it.text }, "Quran text")
        }
        assertTrue(error.message!!, error.message!!.contains("6235"))
    }

    @Test
    fun tamperedPackFileIsRejected() {
        val copy = tmp.newFolder("tampered-pack")
        quranPack.copyRecursively(copy, overwrite = true)
        RandomAccessFile(File(copy, "pack.sqlite"), "rw").use { f ->
            f.seek(f.length() / 2)
            val b = f.read()
            f.seek(f.length() / 2)
            f.write(b xor 0x01)
        }
        val error = assertThrows(PipelineException::class.java) { Pipeline.verify(copy, publicKey) }
        assertTrue(error.message!!.contains("SHA-256 mismatch"))
    }

    @Test
    fun packSignedWithAnotherKeyIsRejected() {
        val keys = tmp.newFolder("other-keys")
        Signing.generateKeyPair(File(keys, "p"), File(keys, "q"))
        val otherPublic = Signing.loadPublic(File(keys, "q"))
        assertThrows(PipelineException::class.java) { Pipeline.verify(quranPack, otherPublic) }
    }

    @Test
    fun editedManifestFailsSignatureCheck() {
        val copy = tmp.newFolder("edited-manifest")
        quranPack.copyRecursively(copy, overwrite = true)
        val file = File(copy, "manifest.signed.json")
        val signed = Signing.decode(file.readText())
        val manifest = String(java.util.Base64.getDecoder().decode(signed.manifest)).replace("2026.10.1", "2099.1.1")
        file.writeText(
            Signing.encode(
                signed.copy(manifest = java.util.Base64.getEncoder().encodeToString(manifest.toByteArray()))
            ),
        )
        val error = assertThrows(PipelineException::class.java) { Pipeline.verify(copy, publicKey) }
        assertEquals("Invalid manifest signature", error.message)
    }
}
