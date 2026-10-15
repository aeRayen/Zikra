package com.zikra.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zikra.app.ui.CURRENT_LOCATION_TAG
import com.zikra.app.ui.RESULT_TAG
import com.zikra.app.ui.SEARCH_FIELD_TAG
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * WP 1.13 acceptance on a real Android runtime: with airplane mode on, the real MainActivity
 * (AppCompat theme, ZikraTheme, navigation) starts as a guest and every implemented screen works.
 */
@RunWith(AndroidJUnit4::class)
class OfflineLaunchTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun str(id: Int) = rule.activity.getString(id)

    @Test
    fun guestUsesTheAppWithoutAnyNetwork() {
        rule.onNodeWithText(str(R.string.home_empty_title)).assertIsDisplayed()
        rule.onNodeWithText(str(R.string.nav_more)).performClick()
        rule.onNodeWithText(str(R.string.settings_language)).performClick()
        rule.onNodeWithText(str(R.string.language_follow_device)).assertIsDisplayed()
    }

    /** WP 2.1 acceptance: with no network and no location permission, a city can still be found and chosen. */
    @Test
    fun cityCanBeChosenOfflineWithoutLocationPermission() {
        rule.onNodeWithText(str(R.string.nav_more)).performClick()
        rule.onNodeWithText(str(R.string.settings_location)).performClick()
        rule.onNodeWithTag(SEARCH_FIELD_TAG).performTextInput("Makkah")
        // First search copies and opens the bundled database: allow a few seconds on a slow emulator.
        waitFor(RESULT_TAG)
        rule.onAllNodesWithTag(RESULT_TAG)[0].performClick()
        waitFor(CURRENT_LOCATION_TAG)
    }

    private fun waitFor(tag: String) {
        rule.waitUntil(SEARCH_TIMEOUT_MS) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    companion object {
        private const val SEARCH_TIMEOUT_MS = 15_000L

        private fun shell(command: String) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).close()
        }

        @BeforeClass
        @JvmStatic
        fun airplaneModeOn() = shell("cmd connectivity airplane-mode enable")

        @AfterClass
        @JvmStatic
        fun airplaneModeOff() = shell("cmd connectivity airplane-mode disable")
    }
}
