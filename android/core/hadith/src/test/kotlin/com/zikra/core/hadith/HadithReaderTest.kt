package com.zikra.core.hadith

import android.database.sqlite.SQLiteDatabase
import com.zikra.core.i18n.SearchNormalizer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** WP 3.4c: reading a pack in the schema the pipeline writes (HadithPackWriter). Placeholder words only. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HadithReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { File(tmp.root, "p.sqlite") }
    private val reader by lazy {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE hadith_section (id INTEGER PRIMARY KEY NOT NULL, title_en TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE hadith (number TEXT PRIMARY KEY NOT NULL, number_value REAL NOT NULL, " +
                    "section INTEGER NOT NULL, text TEXT NOT NULL, search_text TEXT NOT NULL)",
            )
            db.execSQL(
                "CREATE TABLE hadith_grade (number TEXT NOT NULL, ord INTEGER NOT NULL, scholar TEXT NOT NULL, " +
                    "grade TEXT NOT NULL, PRIMARY KEY (number, ord))",
            )
            db.execSQL("INSERT INTO hadith_section VALUES (1, 'Purification'), (2, 'Prayer')")
            val rows = listOf(
                "10" to "نَصٌّ عَاشِرٌ",
                "2" to "نَصٌّ ثَانٍ",
                "2.01" to "نَصٌّ آخَرُ",
                "11" to "كَلِمَةٌ",
            )
            rows.forEach { (n, t) ->
                val section = if (n == "11") 2 else 1
                db.execSQL(
                    "INSERT INTO hadith VALUES (?,?,?,?,?)",
                    arrayOf(n, n.toDouble(), section, t, SearchNormalizer.normalize(t)),
                )
            }
            db.execSQL(
                "INSERT INTO hadith_grade VALUES ('2', 0, 'Al-Albani', 'Sahih'), ('2', 1, 'Zubair Ali Zai', 'Daif')",
            )
        }
        HadithReader.open(HadithBook.ABU_DAWUD, file)
    }

    @After
    fun close() = reader.close()

    @Test
    fun chaptersCountTheirHadithsAndHadithsKeepBookOrder() {
        assertEquals(listOf(HadithSection(1, "Purification", 3), HadithSection(2, "Prayer", 1)), reader.sections())
        // By number, not as text: 2, 2.01, 10 (not "10" before "2").
        assertEquals(listOf("2", "2.01", "10"), reader.section(1).map { it.number })
    }

    @Test
    fun everyGradingKeepsItsScholarAndOrderSoDisagreementShows() {
        val hadith = reader.section(1).first { it.number == "2" }
        assertEquals(listOf(HadithGrade("Al-Albani", "Sahih"), HadithGrade("Zubair Ali Zai", "Daif")), hadith.grades)
        assertTrue(reader.section(1).first { it.number == "10" }.grades.isEmpty())
    }

    @Test
    fun searchIgnoresTashkeelAndNeedsTwoLetters() {
        assertEquals(listOf("2", "2.01", "10"), reader.search("نص").map { it.number })
        assertEquals(listOf("11"), reader.search("كلمة").map { it.number })
        assertTrue(reader.search("ن").isEmpty())
        assertTrue(reader.search("%%").isEmpty())
    }

    @Test
    fun knownTermsAreArabicAndOthersStayAsPublished() {
        assertEquals("صحيح", HadithTerms.gradeArabic("Sahih"))
        assertEquals("إسناده حسن", HadithTerms.gradeArabic("Isnaad Hasan"))
        assertEquals("الألباني", HadithTerms.scholarArabic("Al-Albani"))
        assertNull(HadithTerms.gradeArabic("Sahih (some note)"))
        assertTrue(HadithTerms.isEmpty(HadithGrade("Al-Albani", "-")))
        assertEquals("صحيح البخاري", HadithTerms.title(HadithBook.BUKHARI, arabic = true))
    }
}
