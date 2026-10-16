package com.zikra.app.quran

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.AboutQuranTextScreen
import com.zikra.app.ui.QuranBookmarksScreen
import com.zikra.app.ui.QuranIndexScreen
import com.zikra.app.ui.QuranReaderScreen
import com.zikra.app.ui.QuranSearchScreen
import com.zikra.app.ui.ReaderActions
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.TranslationInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuranIndexDestination(
    onOpen: (Int, Int) -> Unit,
    onAbout: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit,
    onMemorization: () -> Unit,
) {
    val container = appContainer()
    val viewModel: QuranIndexViewModel = viewModel(
        factory = viewModelFactory { initializer { QuranIndexViewModel({ container.quran }, Dispatchers.IO) } },
    )
    val state by viewModel.uiState.collectAsState()
    val continueAt by remember { continueReading(container.quranUserData, { container.quran }, Dispatchers.IO) }
        .collectAsState(initial = null)
    QuranIndexScreen(
        state = state,
        onOpen = onOpen,
        onAbout = onAbout,
        onSearch = onSearch,
        onBookmarks = onBookmarks,
        continueAt = continueAt,
        onMemorization = onMemorization,
    )
}

@Composable
fun QuranReaderDestination(surah: Int, ayah: Int, onBack: () -> Unit, onAbout: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranReaderViewModel = viewModel(
        key = "reader-$surah-$ayah",
        factory = viewModelFactory {
            initializer {
                QuranReaderViewModel(
                    container.quranContent,
                    container.readerPreferences,
                    container.quranUserData,
                    Dispatchers.IO,
                    surah,
                    ayah,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val display by viewModel.display.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val night = display.night ?: isSystemInDarkTheme()
    val uiLanguage = LocalConfiguration.current.locales[0].language
    val translationLanguage = (state as? ReaderUiState.Ready)?.translationInfo?.language
    // Default: show it when the app speaks the translation's language (never replaces the Arabic).
    val showTranslation = translationLanguage != null &&
        (display.showTranslation ?: (uiLanguage == translationLanguage))
    QuranReaderScreen(
        state = state,
        textScale = display.textScale,
        night = night,
        bookmarks = bookmarks,
        showTranslation = showTranslation,
        actions = object : ReaderActions {
            override val onBack = onBack
            override val onSmaller = viewModel::smaller
            override val onBigger = viewModel::bigger
            override val onToggleNight = viewModel::toggleNight
            override val onAbout = onAbout
            override val onToggleBookmark = viewModel::toggleBookmark
            override val onRead = viewModel::onRead
            override val onToggleTranslation = viewModel::toggleTranslation
        },
    )
}

@Composable
fun AboutQuranTextDestination(onBack: () -> Unit) {
    val container = appContainer()
    var info by remember { mutableStateOf("" to "") }
    var translation by remember { mutableStateOf<TranslationInfo?>(null) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            info = try {
                container.quran.sourceVersion() to container.quran.licenseNotice()
            } catch (_: QuranIntegrityException) {
                "" to ""
            }
            translation = container.quranContent.translation()?.info
        }
    }
    AboutQuranTextScreen(
        sourceVersion = info.first,
        licenseNotice = info.second,
        onBack = onBack,
        translation = translation
    )
}

@Composable
fun QuranSearchDestination(onOpen: (Int, Int) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranSearchViewModel = viewModel(
        factory = viewModelFactory { initializer { QuranSearchViewModel(container.quranContent, Dispatchers.IO) } },
    )
    val state by viewModel.uiState.collectAsState()
    QuranSearchScreen(state = state, onQueryChange = viewModel::onQueryChange, onOpen = onOpen, onBack = onBack)
}

@Composable
fun QuranBookmarksDestination(onOpen: (Int, Int) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranBookmarksViewModel = viewModel(
        factory = viewModelFactory {
            initializer { QuranBookmarksViewModel(container.quranUserData, { container.quran }, Dispatchers.IO) }
        },
    )
    val bookmarks by viewModel.bookmarks.collectAsState()
    QuranBookmarksScreen(bookmarks = bookmarks, onOpen = onOpen, onRemove = viewModel::remove, onBack = onBack)
}
