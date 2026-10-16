package com.zikra.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.zikra.app.adhkar.AdhkarChapterUiState
import com.zikra.app.adhkar.AdhkarIndexUiState
import com.zikra.app.ui.AdhkarChapterActions
import com.zikra.app.ui.AdhkarChapterScreen
import com.zikra.app.ui.AdhkarIndexScreen
import com.zikra.core.adhkar.AdhkarBook
import com.zikra.core.adhkar.Dhikr
import com.zikra.core.designsystem.theme.ZikraTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden screenshots of the adhkar screens (WP 2.14b): Arabic text, verses in the mushaf font, counters. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class AdhkarScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = AdhkarBook.open(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = book.close()

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.mainClock.autoAdvance = false
        rule.setContent(content)
        rule.mainClock.advanceTimeBy(FRAME_MS)
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    private fun index(lang: String) = shot("adhkar_index_$lang") {
        val chapters = book.chapters()
        val daily = AdhkarBook.DAILY.map { id -> chapters.first { it.id == id } }
        ZikraTheme { AdhkarIndexScreen(AdhkarIndexUiState.Ready(daily, chapters), onOpen = {}, onQueryChange = {}) }
    }

    /** Morning and evening: Ayat al-Kursi first (a verse in the Tanzil text), then a dhikr said three times. */
    private fun chapter(lang: String) = shot("adhkar_chapter_$lang") {
        val chapter = book.chapters().first { it.id == AdhkarBook.MORNING_EVENING }
        val adhkar = book.chapter(chapter.id)
        val state = AdhkarChapterUiState.Ready(chapter, adhkar, book.info)
        ZikraTheme { AdhkarChapterScreen(state, counts = mapOf(adhkar[0].id to 1), actions = NoActions) }
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabicIndex() = index("ar")

    @Test
    @Config(qualifiers = "ar")
    fun arabicChapter() = chapter("ar")

    @Test
    fun englishChapter() = chapter("en")

    private object NoActions : AdhkarChapterActions {
        override val onBack = {}
        override val onCount: (Dhikr) -> Unit = {}
        override val onReset = {}
    }

    private companion object {
        const val FRAME_MS = 500L
    }
}
