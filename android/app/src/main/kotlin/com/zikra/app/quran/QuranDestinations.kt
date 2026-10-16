package com.zikra.app.quran

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.AboutQuranTextScreen
import com.zikra.app.ui.QuranIndexScreen
import com.zikra.app.ui.QuranReaderScreen
import com.zikra.app.ui.ReaderActions
import com.zikra.core.quran.QuranIntegrityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuranIndexDestination(onOpen: (Int, Int) -> Unit, onAbout: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranIndexViewModel = viewModel(
        factory = viewModelFactory { initializer { QuranIndexViewModel({ container.quran }, Dispatchers.IO) } },
    )
    val state by viewModel.uiState.collectAsState()
    QuranIndexScreen(state = state, onOpen = onOpen, onAbout = onAbout)
}

@Composable
fun QuranReaderDestination(surah: Int, ayah: Int, onBack: () -> Unit, onAbout: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranReaderViewModel = viewModel(
        key = "reader-$surah-$ayah",
        factory = viewModelFactory {
            initializer {
                QuranReaderViewModel(
                    { container.quran },
                    container.readerPreferences,
                    Dispatchers.IO,
                    surah,
                    ayah
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val display by viewModel.display.collectAsState()
    val night = display.night ?: isSystemInDarkTheme()
    QuranReaderScreen(
        state = state,
        textScale = display.textScale,
        night = night,
        actions = ReaderActions(
            onBack = onBack,
            onSmaller = viewModel::smaller,
            onBigger = viewModel::bigger,
            onToggleNight = viewModel::toggleNight,
            onAbout = onAbout,
        ),
    )
}

@Composable
fun AboutQuranTextDestination(onBack: () -> Unit) {
    val container = appContainer()
    var info by remember { mutableStateOf("" to "") }
    LaunchedEffect(Unit) {
        info = withContext(Dispatchers.IO) {
            try {
                container.quran.sourceVersion() to container.quran.licenseNotice()
            } catch (_: QuranIntegrityException) {
                "" to ""
            }
        }
    }
    AboutQuranTextScreen(sourceVersion = info.first, licenseNotice = info.second, onBack = onBack)
}
