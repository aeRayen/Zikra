package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zikra.app.home.HomeCard
import com.zikra.app.navigation.TopLevelDestination
import com.zikra.app.ui.ayahTag
import com.zikra.app.ui.homeCardTag
import com.zikra.app.ui.surahRowTag
import com.zikra.core.designsystem.theme.ZikraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NavigationShellTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    private fun launch(width: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        rule.setContent { ZikraTheme { ZikraApp(widthSizeClass = width) } }
    }

    @Test
    fun startsOnTodayWithTheOnlyImplementedCard() {
        launch()
        // No location yet: the prayer feature asks for one; nothing else is shown (no sample data).
        waitForTag(homeCardTag(HomeCard.ChoosePrayerLocation))
        rule.onNodeWithText(str(R.string.home_empty_title)).assertDoesNotExist()
    }

    @Test
    fun navigatesTodayToMoreToLanguageAndBack() {
        launch()
        rule.onNodeWithText(str(R.string.nav_more)).performClick()
        rule.onNodeWithText(str(R.string.settings_language)).assertIsDisplayed().performClick()
        rule.onNodeWithText(str(R.string.language_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.language_follow_device)).assertIsDisplayed()

        rule.onNodeWithContentDescription(str(R.string.nav_back)).performClick()
        rule.onNodeWithText(str(R.string.settings_language)).assertIsDisplayed()

        rule.onNodeWithText(str(R.string.nav_today)).performClick()
        waitForTag(homeCardTag(HomeCard.ChoosePrayerLocation))
    }

    @Test
    fun prayerTabWithoutLocationLeadsToTheLocationPicker() {
        launch()
        rule.onNodeWithTag(tabTag(TopLevelDestination.PRAYER)).performClick()
        rule.waitUntil(
            WAIT_MS
        ) { rule.onAllNodesWithText(str(R.string.prayer_no_location_title)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(str(R.string.prayer_choose_location)).performClick()
        rule.onNodeWithText(str(R.string.location_title)).assertIsDisplayed()
    }

    @Test
    fun quranTabOpensTheIndexAndASurah() {
        launch()
        rule.onNodeWithTag(tabTag(TopLevelDestination.QURAN)).performClick()
        waitForTag(surahRowTag(1))
        rule.onNodeWithTag(surahRowTag(1)).performClick()
        waitForTag(ayahTag(1))
    }

    @Test
    fun wideScreensUseNavigationRailAndStillNavigate() {
        launch(WindowWidthSizeClass.Expanded)
        rule.onNodeWithText(str(R.string.nav_more)).performClick()
        rule.onNodeWithText(str(R.string.settings_language)).assertIsDisplayed()
    }

    private fun tabLeft(tab: TopLevelDestination) =
        rule.onNodeWithTag(tabTag(tab)).fetchSemanticsNode().boundsInRoot.left

    /** RTL check without screenshots: in Arabic the first tab («اليوم») must sit at the right (start). */
    @Test
    @Config(qualifiers = "ar")
    fun arabicBottomBarIsMirrored() {
        launch()
        rule.onNodeWithTag(tabTag(TopLevelDestination.TODAY)).assertTextContains("اليوم")
        val today = tabLeft(TopLevelDestination.TODAY)
        val more = tabLeft(TopLevelDestination.MORE)
        assertTrue("Today ($today) should be right of More ($more) in RTL", today > more)
    }

    @Test
    fun englishBottomBarIsLeftToRight() {
        launch()
        val today = tabLeft(TopLevelDestination.TODAY)
        val more = tabLeft(TopLevelDestination.MORE)
        assertTrue("Today ($today) should be left of More ($more) in LTR", today < more)
    }

    private fun waitForTag(tag: String) =
        rule.waitUntil(WAIT_MS) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
