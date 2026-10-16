package com.zikra.app.hadith

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.zikra.app.ui.HadithBooksScreen
import com.zikra.app.ui.HadithListScreen
import com.zikra.app.ui.downloadTag
import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.hadith.Hadith
import com.zikra.core.hadith.HadithBook
import com.zikra.core.hadith.HadithGrade
import com.zikra.core.network.Transport
import com.zikra.core.packs.PackInstaller
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
/** WP 3.4c: the hadith library on the phone, and its screens. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HadithAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val offline = Transport { AppResult.Failure(AppError.Offline) }

    /** A pack as installed by PackInstaller: the database and its line in installed.json. */
    private fun installedRoot(): File {
        val root = File(tmp.root, "packs").apply { mkdirs() }
        val edition = HadithBook.ABU_DAWUD.editionCode
        SQLiteDatabase.openOrCreateDatabase(File(root, "$edition-1.sqlite"), null).use { db ->
            db.execSQL("CREATE TABLE hadith_section (id INTEGER PRIMARY KEY NOT NULL, title_en TEXT NOT NULL)")
            db.execSQL(
                "CREATE TABLE hadith (number TEXT, number_value REAL, section INTEGER, text TEXT, search_text TEXT)",
            )
            db.execSQL("CREATE TABLE hadith_grade (number TEXT, ord INTEGER, scholar TEXT, grade TEXT)")
            db.execSQL("INSERT INTO hadith_section VALUES (1, 'Purification')")
            db.execSQL("INSERT INTO hadith VALUES ('1', 1, 1, 'نص', 'نص')")
        }
        File(root, "installed.json").writeText(
            """{"$edition":{"editionCode":"$edition","version":"1","packId":"p","fileName":"$edition-1.sqlite"}}""",
        )
        return root
    }

    @Test
    fun installedBooksOpenAndAFailedDownloadSaysWhy() = runTest {
        val installer = PackInstaller(installedRoot(), offline, appVersion = "1.0.0")
        val library = HadithLibrary({ installer }, UnconfinedTestDispatcher(testScheduler))
        library.refresh()
        assertEquals(BookState.Installed, library.books.value[HadithBook.ABU_DAWUD])
        assertEquals(BookState.NotDownloaded, library.books.value[HadithBook.BUKHARI])
        assertEquals(listOf("1"), library.reader(HadithBook.ABU_DAWUD)?.section(1)?.map { it.number })

        library.download(HadithBook.BUKHARI)
        assertEquals(BookState.Failed(AppError.Offline), library.books.value[HadithBook.BUKHARI])
        // Nothing else changed.
        assertEquals(BookState.Installed, library.books.value[HadithBook.ABU_DAWUD])
    }

    @Test
    fun theBooksScreenOffersADownloadAndAsksAgainAfterAFailure() {
        val downloads = mutableListOf<HadithBook>()
        val states = mapOf(
            HadithBook.BUKHARI to BookState.Installed,
            HadithBook.MUSLIM to BookState.Failed(AppError.Offline),
        )
        rule.setContent {
            ZikraTheme {
                HadithBooksScreen(states, onOpen = {}, onDownload = { downloads += it }, onSearch = {}, onBack = {})
            }
        }
        rule.onNodeWithTag(downloadTag(HadithBook.MUSLIM)).performClick()
        rule.onNodeWithTag(downloadTag(HadithBook.ABU_DAWUD)).performClick()
        assertEquals(listOf(HadithBook.MUSLIM, HadithBook.ABU_DAWUD), downloads)
        rule.onNode(hasText("No connection", substring = true)).assertExists()
    }

    @Test
    @Config(qualifiers = "ar")
    fun gradingsShowInArabicWithTheirScholarsAndUnknownOnesAsPublished() {
        val hadith = Hadith(
            HadithBook.ABU_DAWUD,
            "5073",
            43,
            "نص",
            listOf(
                HadithGrade("Al-Albani", "Daif"),
                HadithGrade("Zubair Ali Zai", "Sahih (note)"),
                HadithGrade("X", "-"),
            ),
        )
        rule.setContent { ZikraTheme { HadithListScreen("t", listOf(hadith), onBack = {}) } }
        val card = rule.onNodeWithTag("hadith_abudawud_5073")
        card.assertTextContains("الألباني", substring = true)
        card.assertTextContains("ضعيف", substring = true)
        card.assertTextContains("Sahih (note)", substring = true)
        rule.onNode(hasText("X", substring = true)).assertDoesNotExist()
    }
}
