package com.zikra.app.adhkar

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.SpanStyle
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.ui.ADHKAR_LIST_TAG
import com.zikra.app.ui.ADHKAR_PROGRESS_TAG
import com.zikra.app.ui.AdhkarChapterActions
import com.zikra.app.ui.AdhkarChapterScreen
import com.zikra.app.ui.ayahNumberMark
import com.zikra.app.ui.countTag
import com.zikra.app.ui.dhikrText
import com.zikra.app.ui.referenceTag
import com.zikra.app.ui.referenceTextTag
import com.zikra.app.ui.withoutMarker
import com.zikra.core.adhkar.AdhkarBook
import com.zikra.core.adhkar.Dhikr
import com.zikra.core.adhkar.DhikrPart
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.settings.AdhkarProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdhkarTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val book = AdhkarBook.open(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() {
        book.close()
        Dispatchers.resetMain()
    }

    private suspend fun <T> Flow<T>.await(condition: (T) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(10_000) { first(condition) } }

    @Test
    fun indexOffersTheDailyChaptersFirstAndFiltersByTitle() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = AdhkarIndexViewModel({ book }, UnconfinedTestDispatcher(testScheduler))
        val ready = vm.uiState.await { it is AdhkarIndexUiState.Ready } as AdhkarIndexUiState.Ready
        assertEquals(AdhkarBook.DAILY, ready.daily.map { it.id })
        assertEquals(AdhkarBook.CHAPTER_COUNT, ready.chapters.size)
        // Without tashkeel or hamza, as people type.
        vm.onQueryChange("النوم")
        val sleep = vm.uiState.value as AdhkarIndexUiState.Ready
        assertTrue(sleep.chapters.any { it.id == AdhkarBook.SLEEP })
        assertTrue(sleep.chapters.size < 10)
        vm.onQueryChange("")
        assertEquals(AdhkarBook.CHAPTER_COUNT, (vm.uiState.value as AdhkarIndexUiState.Ready).chapters.size)
    }

    @Test
    fun countsStopAtTheRepeatAndSurviveANewViewModel() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val io = UnconfinedTestDispatcher(testScheduler)
        val progress = AdhkarProgressStore({ File(tmp.root, "p.json") }, backgroundScope) { LocalDate.of(2026, 10, 16) }
        val vm = AdhkarChapterViewModel({ book }, progress, io, AdhkarBook.MORNING_EVENING)
        val ready = vm.uiState.await { it is AdhkarChapterUiState.Ready } as AdhkarChapterUiState.Ready
        val thrice = ready.adhkar.first { it.repeat == 3 }
        repeat(4) { vm.count(thrice) }
        vm.counts.await { it[thrice.id] == 3 }
        assertFalse(vm.saveFailed.value)

        // The screen is closed and opened again (or the app restarted): the count is still there.
        val again = AdhkarChapterViewModel({ book }, progress, io, AdhkarBook.MORNING_EVENING)
        again.counts.await { it[thrice.id] == 3 }
        again.uiState.await { it is AdhkarChapterUiState.Ready }
        again.reset()
        again.counts.await { it[thrice.id] == null }
    }

    @Test
    fun quotedVersesUseTheMushafFontAndCarryTheirAyahNumber() {
        val kursi = book.chapter(AdhkarBook.MORNING_EVENING).first { it.id == 75 }
        val quranStyle = SpanStyle(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified)
        val text = dhikrText(kursi.parts, quranStyle)
        val verse = kursi.parts.filterIsInstance<DhikrPart.Quran>().single()
        assertTrue(text.text.contains(verse.text + " " + ayahNumberMark(255)))
        val span = text.spanStyles.single()
        assertEquals(verse.text, text.text.substring(span.start, span.start + verse.text.length))
        assertFalse(withoutMarker(kursi.references.single()).startsWith("("))
    }

    private fun showChapter(adhkar: List<Dhikr>, counts: Map<Int, Int>, counted: MutableList<Int>) {
        val chapter = book.chapters().first { it.id == AdhkarBook.MORNING_EVENING }
        rule.setContent {
            ZikraTheme {
                AdhkarChapterScreen(
                    state = AdhkarChapterUiState.Ready(chapter, adhkar, book.info),
                    counts = counts,
                    actions = object : AdhkarChapterActions {
                        override val onBack = {}
                        override val onCount: (Dhikr) -> Unit = { counted += it.id }
                        override val onReset = {}
                    },
                )
            }
        }
    }

    @Test
    fun tappingCountsAndAFinishedDhikrCannotBeCountedAgain() {
        val adhkar = book.chapter(AdhkarBook.MORNING_EVENING)
        val first = adhkar.first()
        val counted = mutableListOf<Int>()
        showChapter(adhkar, mapOf(adhkar[1].id to adhkar[1].repeat), counted)
        rule.onNodeWithTag(ADHKAR_PROGRESS_TAG).assertTextContains("1 of ${adhkar.size}", substring = true)
        rule.onNodeWithTag(countTag(first.id)).performClick()
        assertEquals(listOf(first.id), counted)
        rule.onNodeWithTag(ADHKAR_LIST_TAG).performScrollToNode(hasTestTag(countTag(adhkar[1].id)))
        rule.onNodeWithTag(countTag(adhkar[1].id)).assertIsNotEnabled()
    }

    @Test
    fun theReferenceIsShownOnRequest() {
        val adhkar = book.chapter(AdhkarBook.MORNING_EVENING)
        val kursi = adhkar.first { it.id == 75 }
        showChapter(adhkar, emptyMap(), mutableListOf())
        rule.onNodeWithTag(ADHKAR_LIST_TAG).performScrollToNode(hasTestTag(referenceTag(kursi.id)))
        rule.onNodeWithTag(referenceTag(kursi.id)).performClick()
        val reference = hasText("سورة البقرة", substring = true) and
            hasAnyAncestor(hasTestTag(referenceTextTag(kursi.id)))
        rule.onNode(reference, useUnmergedTree = true).assertExists()
    }
}
