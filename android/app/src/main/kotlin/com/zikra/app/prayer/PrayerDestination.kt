package com.zikra.app.prayer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.PrayerTimesScreen
import kotlinx.coroutines.Dispatchers

/** Navigation entry of the prayer tab. */
@Composable
fun PrayerDestination(onChooseLocation: () -> Unit, onOpenSettings: () -> Unit, onOpenMonth: () -> Unit) {
    val container = appContainer()
    val viewModel: PrayerTimesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PrayerTimesViewModel(
                    places = { container.places },
                    locationStore = container.locationStore,
                    preferencesStore = container.prayerPreferences,
                    io = Dispatchers.IO,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }
    PrayerTimesScreen(
        state = state,
        onChooseLocation = onChooseLocation,
        onOpenSettings = onOpenSettings,
        onOpenMonth = onOpenMonth,
    )
}
