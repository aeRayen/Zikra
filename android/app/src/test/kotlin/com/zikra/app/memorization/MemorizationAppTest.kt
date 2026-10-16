package com.zikra.app.memorization

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zikra.app.ui.MemorizationActions
import com.zikra.app.ui.MemorizationScreen
import com.zikra.app.ui.PLAN_SAVE_TAG
import com.zikra.app.ui.memorizedTag
import com.zikra.core.database.user.UserDatabase
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall
import com.zikra.core.quran.QuranText
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** WP 3.9: memorization through user.db and the bundled mushaf. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MemorizationAppTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val quran = QuranText.open(context)
    private val db = Room.inMemoryDatabaseBuilder(context, UserDatabase::class.java).allowMainThreadQueries().build()
    private val data = MemorizationData(db.memorizationDao())
    private val day = LocalDate.of(2026, 10, 16)

    @After
    fun tearDown() {
        quran.close()
        db.close()
        Dispatchers.resetMain()
    }

    private suspend fun <T> Flow<T>.await(condition: (T) -> Boolean) =
        withContext(Dispatchers.Default) { withTimeout(10_000) { first(condition) } }

    @Test
    fun theMushafHas604PagesStartingWhereTheyShould() {
        val pages = mushafPages(quran)
        assertEquals(604, pages.size)
        assertEquals(1 to 1, pages.first().let { it.surah to it.ayah })
        assertEquals(2 to 1, pages[1].let { it.surah to it.ayah })
        assertEquals(30, pages.last().juz)
        assertEquals(23, pages.count { it.juz == 30 })
    }

    @Test
    fun aPlanFromTheEndGivesPage604ThenItsReviewTheNextDay() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val io = UnconfinedTestDispatcher(testScheduler)
        val vm = MemorizationViewModel(data, { quran }, io) { day }
        vm.uiState.await { it.loaded }
        vm.setPlan(Direction.FROM_END, Pace.ONE_PAGE)
        assertEquals(listOf(604), vm.uiState.await { it.newPages.isNotEmpty() }.newPages.map { it.page })
        vm.memorized(604)
        val after = vm.uiState.await { it.memorized == 1 }
        assertTrue(after.newPages.isEmpty())
        assertTrue(after.reviews.isEmpty())

        // Tomorrow: page 603 is new, 604 is due for review; a good recall moves it out of today.
        val tomorrow = MemorizationViewModel(data, { quran }, io) { day.plusDays(1) }
        val next = tomorrow.uiState.await { it.loaded && it.plan != null }
        assertEquals(listOf(603), next.newPages.map { it.page })
        assertEquals(listOf(604), next.reviews.map { it.page })
        tomorrow.reviewed(604, Recall.GOOD)
        tomorrow.uiState.await { it.reviews.isEmpty() }
    }

    @Test
    fun aWholeJuzMemorizedBeforeCountsWithoutTakingTodaysPage() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val vm = MemorizationViewModel(data, { quran }, UnconfinedTestDispatcher(testScheduler)) { day }
        vm.uiState.await { it.loaded }
        vm.setPlan(Direction.FROM_END, Pace.ONE_PAGE)
        vm.memorizedJuz(30)
        val state = vm.uiState.await { it.memorized == 23 }
        assertEquals(23, state.juz.first { it.juz == 30 }.memorized)
        // The plan goes on below juz' 30, today.
        assertEquals(1, state.newPages.size)
        assertTrue(state.newPages.single().page < 582)
    }

    @Test
    fun noHomeCardBeforeAPlan() = runTest {
        val cards = MemorizationHomeCards({ data }, UnconfinedTestDispatcher(testScheduler)) { day }
        assertTrue(cards.cards(kotlinx.coroutines.flow.flowOf("ar")).first().isEmpty())
        data.setPlan(com.zikra.core.memorization.MemorizationPlan(Direction.FROM_START, Pace.ONE_PAGE))
        val card = cards.cards(kotlinx.coroutines.flow.flowOf("ar")).first { it.isNotEmpty() }.single()
        assertEquals(com.zikra.app.home.HomeCard.MemorizationToday(1, 0), card)
    }

    @Test
    fun theScreenStartsAPlanAndMarksAPageMemorized() {
        val calls = mutableListOf<String>()
        val pages = mushafPages(quran)
        val actions = object : MemorizationActions {
            override val onBack = {}
            override val onSetPlan: (Direction, Pace) -> Unit = { d, p -> calls += "plan $d $p" }
            override val onOpenPage: (Int, Int) -> Unit = { _, _ -> }
            override val onMemorized: (Int) -> Unit = { calls += "memorized $it" }
            override val onReviewed: (Int, Recall) -> Unit = { _, _ -> }
            override val onMemorizedJuz: (Int) -> Unit = {}
        }
        val withPlan = MemorizationUiState(
            loaded = true,
            plan = com.zikra.core.memorization.MemorizationPlan(Direction.FROM_END, Pace.ONE_PAGE),
            newPages = listOf(pages.last()),
        )
        val shown = androidx.compose.runtime.mutableStateOf(MemorizationUiState(loaded = true))
        rule.setContent { ZikraTheme { MemorizationScreen(shown.value, actions) } }
        rule.onNodeWithTag(PLAN_SAVE_TAG).performClick()
        shown.value = withPlan
        rule.waitForIdle()
        rule.onNodeWithTag(memorizedTag(604)).performClick()
        assertEquals(listOf("plan FROM_END ONE_PAGE", "memorized 604"), calls)
    }
}
