package com.zikra.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zikra.app.ui.LanguageScreen
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.Language
import com.zikra.core.i18n.SupportedLanguages
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanguageScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun showsEveryLanguageByNativeNameAndMarksSelection() {
        rule.setContent {
            ZikraTheme { LanguageScreen(selected = SupportedLanguages.ARABIC, onSelect = {}, onBack = {}) }
        }
        rule.onNodeWithText("العربية").assertIsSelected()
        rule.onNodeWithText("English").assertIsNotSelected()
        rule.onNodeWithText(rule.activity.getString(R.string.language_follow_device)).assertIsNotSelected()
    }

    @Test
    fun selectingReportsLanguageOrNullForDevice() {
        val picked = mutableListOf<Language?>()
        rule.setContent {
            ZikraTheme { LanguageScreen(selected = null, onSelect = { picked += it }, onBack = {}) }
        }
        rule.onNodeWithText("English").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.language_follow_device)).performClick()
        assertEquals(listOf(SupportedLanguages.ENGLISH, null), picked)
    }
}
