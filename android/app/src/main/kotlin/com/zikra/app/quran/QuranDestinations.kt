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
import com.zikra.app.ui.QuranBookmarksScreen
import com.zikra.app.ui.QuranIndexScreen
import com.zikra.app.ui.QuranReaderScreen
import com.zikra.app.ui.QuranSearchScreen
import com.zikra.app.ui.ReaderActions
import com.zikra.core.quran.QuranIntegrityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun QuranIndexDestination(
    onOpen: (Int, Int) -> Unit,
    onAbout: () -> Unit,
    onSearch: () -> Unit,
    onBookmarks: () -> Unit
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
                    { container.quran },
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
    QuranReaderScreen(
        state = state,
        textScale = display.textScale,
        night = night,
        bookmarks = bookmarks,
        actions = object : ReaderActions {
            override val onBack = onBack
            override val onSmaller = viewModel::smaller
            override val onBigger = viewModel::bigger
            override val onToggleNight = viewModel::toggleNight
            override val onAbout = onAbout
            override val onToggleBookmark = viewModel::toggleBookmark
            override val onRead = viewModel::onRead
        },
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

@Composable
fun QuranSearchDestination(onOpen: (Int, Int) -> Unit, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: QuranSearchViewModel = viewModel(
        factory = viewModelFactory { initializer { QuranSearchViewModel({ container.quran }, Dispatchers.IO) } },
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
