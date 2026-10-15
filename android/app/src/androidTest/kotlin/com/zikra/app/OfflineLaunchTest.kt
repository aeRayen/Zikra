package com.zikra.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    companion object {
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
