package com.zikra.app.quran

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.R
import com.zikra.app.ui.QURAN_SEARCH_COUNT_TAG
import com.zikra.app.ui.QuranSearchScreen
import com.zikra.app.ui.searchResultTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.quran.QuranText
import com.zikra.core.quran.QuranTranslation
import com.zikra.core.quran.search
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuranSearchTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val quran = QuranText.open(ApplicationProvider.getApplicationContext())
    private val english = QuranTranslation.openEnglish(ApplicationProvider.getApplicationContext())
    private val content = QuranContent({ quran }, { english })

    @After
    fun tearDown() {
        quran.close()
        english.close()
        Dispatchers.resetMain()
    }

    @Test
    fun typingSearchesAfterTheDebounceAndPairsResultsWithTheirSurah() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = QuranSearchViewModel(content, dispatcher)
        vm.onQueryChange("الرحم")
        vm.onQueryChange("الرحمن")
        advanceUntilIdle()
        val state = vm.uiState.first { it.results != null }
        assertEquals(quran.search("الرحمن").total, state.total)
        val (surah, ayah) = requireNotNull(state.results).first().let { it.surah to it.ayah }
        assertEquals(1, surah.number)
        assertEquals(1, ayah.ayah)
        vm.onQueryChange("")
        assertEquals(null, vm.uiState.value.results)
    }

    private fun show(query: String, onOpen: (Int, Int) -> Unit = { _, _ -> }) {
        val found = quran.search(query)
        val surahs = quran.surahs().associateBy { it.number }
        val results = found.ayahs.map { SearchHit(surahs.getValue(it.surah), it) }
        val state = QuranSearchUiState(query, results = results, total = found.total)
        rule.setContent { ZikraTheme { QuranSearchScreen(state, onQueryChange = {}, onOpen = onOpen, onBack = {}) } }
    }

    @Test
    fun latinLettersSearchTheTranslationAndShowTheArabicAyah() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val vm = QuranSearchViewModel(content, dispatcher)
        vm.onQueryChange("Self-Sustaining")
        advanceUntilIdle()
        val hits = requireNotNull(vm.uiState.first { it.results != null }.results)
        val kursi = hits.first { it.ayah.surah == 2 && it.ayah.ayah == 255 }
        assertEquals(quran.ayahs(2)[254].text, kursi.ayah.text) // Arabic stays verbatim
        assertTrue(requireNotNull(kursi.translation).contains("Self-Sustaining"))
    }

    @Test
    fun resultsShowTheCountAndOpenTheAyah() {
        val opened = mutableListOf<Pair<Int, Int>>()
        show("وأقيموا الصلاة") { s, a -> opened += s to a }
        rule.onNodeWithTag(QURAN_SEARCH_COUNT_TAG).assertTextContains("found", substring = true)
        rule.onNodeWithTag(searchResultTag(2, 43)).performClick()
        assertEquals(listOf(2 to 43), opened)
    }

    @Test
    fun noResultSaysWhatToTry() {
        show("زززز")
        rule.onNodeWithText(rule.activity.getString(R.string.quran_search_none)).assertExists()
    }

    @Test
    fun manyResultsSayOnlyTheFirstAreShown() {
        show("الله")
        assertTrue(quran.search("الله").total > QuranText.SEARCH_LIMIT)
        rule.onNodeWithTag(QURAN_SEARCH_COUNT_TAG).assertExists()
    }
}
