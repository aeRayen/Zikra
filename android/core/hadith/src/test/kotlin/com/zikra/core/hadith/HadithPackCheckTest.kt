package com.zikra.core.hadith

import android.database.sqlite.SQLiteDatabase
import com.zikra.core.packs.PackRejected
import com.zikra.core.packs.PackVerification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HadithPackCheckTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sha(text: String) =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun db(vararg numbers: String): SQLiteDatabase =
        SQLiteDatabase.openOrCreateDatabase(File(tmp.root, "h.sqlite"), null).apply {
            execSQL("CREATE TABLE hadith (number TEXT PRIMARY KEY NOT NULL)")
            numbers.forEach { execSQL("INSERT INTO hadith VALUES ('$it')") }
        }

    @Test
    fun aPackWithExactlyTheNamedHadithsPasses() {
        val verification = PackVerification(itemCount = 3, itemKeysSha256 = sha("1\n2.01\n3"))
        db("1", "2.01", "3").use { HadithPackCheck.check(it, verification) }
    }

    @Test
    fun aMissingOrChangedHadithIsRefused() {
        db("1", "2.01").use { db ->
            assertThrows(PackRejected::class.java) {
                HadithPackCheck.check(db, PackVerification(itemCount = 3, itemKeysSha256 = sha("1\n2.01\n3")))
            }
            assertThrows(PackRejected::class.java) {
                HadithPackCheck.check(db, PackVerification(itemCount = 2, itemKeysSha256 = sha("1\n2")))
            }
        }
    }

    @Test
    fun theCatalogPointsAtThePublishedRelease() {
        val urls = HadithCatalog.urls(HadithBook.IBN_MAJAH)
        assertEquals("${HadithCatalog.RELEASE}/hadith-ibnmajah.manifest.json", urls.manifestUrl)
        assertEquals("ar.ibnmajah.hadith-api", HadithBook.IBN_MAJAH.editionCode)
    }
}
