package com.zikra.app

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.zikra.app.memorization.JuzProgress
import com.zikra.app.memorization.MemorizationUiState
import com.zikra.app.memorization.mushafPages
import com.zikra.app.ui.MemorizationActions
import com.zikra.app.ui.MemorizationScreen
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.MemorizationPlan
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall
import com.zikra.core.quran.QuranText
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Golden screenshots of memorization (WP 3.9): a plan from the end, one new page and two reviews. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xhdpi", application = Application::class)
class MemorizationScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val quran = QuranText.open(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = quran.close()

    private fun shot(name: String) {
        val pages = mushafPages(quran).associateBy { it.page }
        val state = MemorizationUiState(
            loaded = true,
            plan = MemorizationPlan(Direction.FROM_END, Pace.ONE_PAGE),
            newPages = listOf(pages.getValue(601)),
            reviews = listOf(pages.getValue(604), pages.getValue(603)),
            memorized = 3,
            juz = listOf(JuzProgress(30, 3, 23), JuzProgress(29, 0, 20)),
        )
        rule.mainClock.autoAdvance = false
        rule.setContent { ZikraTheme { MemorizationScreen(state, NoActions) } }
        rule.mainClock.advanceTimeBy(FRAME_MS)
        rule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = "ar")
    fun arabic() = shot("memorization_ar")

    @Test
    fun english() = shot("memorization_en")

    private object NoActions : MemorizationActions {
        override val onBack = {}
        override val onSetPlan: (Direction, Pace) -> Unit = { _, _ -> }
        override val onOpenPage: (Int, Int) -> Unit = { _, _ -> }
        override val onMemorized: (Int) -> Unit = {}
        override val onReviewed: (Int, Recall) -> Unit = { _, _ -> }
        override val onMemorizedJuz: (Int) -> Unit = {}
    }

    private companion object {
        const val FRAME_MS = 500L
    }
}
