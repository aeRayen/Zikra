package com.zikra.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.HomeScreen

/** Navigation entry of «Today». */
@Composable
fun HomeDestination(onOpenPrayer: () -> Unit, onChooseLocation: () -> Unit, onContinueReading: (Int, Int) -> Unit) {
    val container = appContainer()
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory { initializer { HomeViewModel(container.homeCardProviders()) } },
    )
    val state by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }
    HomeScreen(
        state = state,
        onOpenPrayer = onOpenPrayer,
        onChooseLocation = onChooseLocation,
        onContinueReading = onContinueReading,
    )
}
