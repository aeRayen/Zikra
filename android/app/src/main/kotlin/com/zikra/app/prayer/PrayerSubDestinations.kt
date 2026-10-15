package com.zikra.app.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.PrayerMonthScreen
import com.zikra.app.ui.PrayerSettingsActions
import com.zikra.app.ui.PrayerSettingsScreen
import kotlinx.coroutines.Dispatchers

/** Navigation entry of the prayer settings screen. */
@Composable
fun PrayerSettingsDestination(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: PrayerSettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PrayerSettingsViewModel(
                    places = { container.places },
                    locationStore = container.locationStore,
                    store = container.prayerPreferences,
                    io = Dispatchers.IO,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    PrayerSettingsScreen(
        state = state,
        actions = PrayerSettingsActions(
            onBack = onBack,
            onMethod = viewModel::setMethod,
            onMadhab = viewModel::setMadhab,
            onHighLatitudeRule = viewModel::setHighLatitudeRule,
            onAdjust = viewModel::adjust,
            onReset = viewModel::reset,
        ),
    )
}

/** Navigation entry of the monthly timetable. */
@Composable
fun PrayerMonthDestination(onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: PrayerMonthViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PrayerMonthViewModel(
                    places = { container.places },
                    locationStore = container.locationStore,
                    preferencesStore = container.prayerPreferences,
                    io = Dispatchers.IO,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    PrayerMonthScreen(state, onBack = onBack, onPrevious = viewModel::previousMonth, onNext = viewModel::nextMonth)
}
