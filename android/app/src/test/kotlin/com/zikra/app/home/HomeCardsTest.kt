package com.zikra.app.home

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zikra.app.R
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.app.ui.HomeScreen
import com.zikra.app.ui.homeCardTag
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeCardsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val ready = PrayerTimesUiState.Ready(
        place = null,
        source = LocationSource.DEVICE,
        zone = ZoneId.of("UTC"),
        zoneDiffersFromDevice = false,
        date = LocalDate.of(2026, 6, 1),
        times = PrayerName.entries.map { it to LocalTime.NOON },
        next = PrayerName.ASR,
        nextTime = LocalTime.of(15, 38),
        nextIsTomorrow = false,
        minutesUntilNext = 80,
        method = CalculationMethodId.MUSLIM_WORLD_LEAGUE,
        methodIsAutomatic = true,
        madhab = AsrMadhab.STANDARD,
    )

    @Test
    fun withoutProvidersHomeIsHonestlyEmpty() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val state = HomeViewModel(emptyList()).uiState.first { it.loaded }
        assertEquals(emptyList<HomeCard>(), state.cards)
    }

    @Test
    fun providersAreCombinedInOrder() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        val a = HomeCardProvider { flowOf(listOf(HomeCard.ChoosePrayerLocation)) }
        val b = HomeCardProvider { flowOf(listOf(HomeCard.NextPrayer(ready))) }
        val state = HomeViewModel(listOf(a, b)).uiState.first { it.loaded }
        assertEquals(listOf("prayer.location", "prayer.next"), state.cards.map { it.key })
    }

    @Test
    fun emptyStateOnlyWhenThereAreNoCards() {
        rule.setContent { ZikraTheme { HomeScreen(HomeUiState(loaded = true)) } }
        rule.onNodeWithText(rule.activity.getString(R.string.home_empty_title)).assertExists()
    }

    @Test
    fun nextPrayerCardShowsPrayerAndCountdownAndOpensThePrayerTab() {
        var opened = 0
        val card = HomeCard.NextPrayer(ready)
        rule.setContent { ZikraTheme { HomeScreen(HomeUiState(true, listOf(card)), onOpenPrayer = { opened++ }) } }
        rule.onNodeWithTag(homeCardTag(card))
            .assertTextContains(rule.activity.getString(R.string.prayer_asr), substring = true)
            .assertTextContains("1 hour and 20 minutes", substring = true)
            .performClick()
        assertEquals(1, opened)
        rule.onNodeWithText(rule.activity.getString(R.string.home_empty_title)).assertDoesNotExist()
    }

    @Test
    fun chooseLocationCardLeadsToThePicker() {
        var chosen = 0
        val state = HomeUiState(true, listOf(HomeCard.ChoosePrayerLocation))
        rule.setContent { ZikraTheme { HomeScreen(state, onChooseLocation = { chosen++ }) } }
        rule.onNodeWithText(rule.activity.getString(R.string.prayer_choose_location)).performClick()
        assertEquals(1, chosen)
    }
}
