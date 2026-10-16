package com.zikra.app.quran

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.R
import com.zikra.app.ui.ABOUT_NOTICE_TAG
import com.zikra.app.ui.AboutQuranTextScreen
import com.zikra.app.ui.QuranReaderScreen
import com.zikra.app.ui.ReaderActions
import com.zikra.app.ui.ayahTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.quran.QuranText
import com.zikra.core.settings.ReaderPreferences
import com.zikra.core.settings.ReaderPreferencesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranReaderTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val quran = QuranText.open(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        quran.close()
        Dispatchers.resetMain()
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.await(condition: (T) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(10_000) { first(condition) } }

    @Test
    fun indexHasEverySurahAndTheThirtyJuz() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = QuranIndexViewModel({ quran }, UnconfinedTestDispatcher(testScheduler))
        val ready = vm.uiState.await { it is QuranIndexUiState.Ready } as QuranIndexUiState.Ready
        assertEquals(114, ready.surahs.size)
        assertEquals(30, ready.juz.size)
        assertEquals(2 to 142, ready.juz[1].let { it.surah.number to it.ayah })
    }

    @Test
    fun readerOpensAtTheRequestedAyahAndScalesInExactSteps() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val prefs = ReaderPreferencesStore({ File(tmp.root, "reader.json") }, backgroundScope)
        val vm = QuranReaderViewModel({ quran }, prefs, UnconfinedTestDispatcher(testScheduler), 2, 255)
        val ready = vm.uiState.await { it is ReaderUiState.Ready } as ReaderUiState.Ready
        assertEquals(286, ready.ayahs.size)
        assertEquals(254, ready.startIndex) // Ayat al-Kursi
        vm.bigger()
        prefs.data.await { it.textScale == 1.1f }
        // One step at a time (like taps). 1.1 - 0.1 - 0.1 must be exactly 0.9, not 0.8999...
        for (expected in listOf(1.0f, 0.9f, ReaderPreferences.MIN_SCALE, ReaderPreferences.MIN_SCALE)) {
            vm.smaller()
            prefs.data.await { it.textScale == expected }
        }
    }

    private fun showReader(surah: Int, night: Boolean = false) {
        val ayahs = quran.ayahs(surah)
        val state = ReaderUiState.Ready(requireNotNull(quran.surah(surah)), ayahs, 0)
        val actions = ReaderActions({}, {}, {}, {}, {})
        rule.setContent { QuranReaderScreen(state, textScale = 1f, night = night, actions = actions) }
    }

    @Test
    fun ayahIsShownVerbatimWithItsEndMark() {
        showReader(1)
        val first = quran.ayahs(1).first()
        rule.onNodeWithTag(ayahTag(1)).assertTextEquals(first.text + " ۝١")
    }

    @Test
    fun sizeButtonsHaveSpokenLabels() {
        showReader(112)
        val context = rule.activity
        rule.onNodeWithContentDescription(context.getString(R.string.reader_bigger)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.reader_smaller)).performClick()
        rule.onNodeWithContentDescription(context.getString(R.string.reader_night)).performClick()
    }

    @Test
    fun aboutShowsTheTanzilNoticeAsPublished() {
        val notice = quran.licenseNotice()
        rule.setContent { ZikraTheme { AboutQuranTextScreen(quran.sourceVersion(), notice, onBack = {}) } }
        rule.onNodeWithTag(ABOUT_NOTICE_TAG).assertTextEquals(notice)
        assertTrue(notice.contains("Tanzil", ignoreCase = true))
    }
}
