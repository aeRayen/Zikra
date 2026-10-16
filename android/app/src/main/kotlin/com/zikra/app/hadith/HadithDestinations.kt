package com.zikra.app.hadith

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.HadithBooksScreen
import com.zikra.app.ui.HadithChaptersScreen
import com.zikra.app.ui.HadithListScreen
import com.zikra.app.ui.HadithSearchScreen
import com.zikra.core.hadith.HadithBook
import com.zikra.core.hadith.HadithTerms
import com.zikra.core.i18n.NumeralSystem
import kotlinx.coroutines.Dispatchers

@Composable
fun HadithBooksDestination(onOpen: (HadithBook) -> Unit, onSearch: () -> Unit, onBack: () -> Unit) {
    val library = appContainer().hadith
    val viewModel: HadithBooksViewModel = viewModel(
        factory = viewModelFactory { initializer { HadithBooksViewModel(library) } },
    )
    val books by viewModel.books.collectAsState()
    HadithBooksScreen(books, onOpen = onOpen, onDownload = viewModel::download, onSearch = onSearch, onBack = onBack)
}

@Composable
fun HadithChaptersDestination(book: HadithBook, onOpen: (Int) -> Unit, onBack: () -> Unit) {
    val library = appContainer().hadith
    val viewModel: HadithChaptersViewModel = viewModel(
        key = "hadith-${book.key}",
        factory = viewModelFactory { initializer { HadithChaptersViewModel(library, book, Dispatchers.IO) } },
    )
    val sections by viewModel.sections.collectAsState()
    HadithChaptersScreen(bookTitle(book), sections, onOpen, onBack)
}

@Composable
fun HadithSectionDestination(book: HadithBook, section: Int, onBack: () -> Unit) {
    val library = appContainer().hadith
    val viewModel: HadithSectionViewModel = viewModel(
        key = "hadith-${book.key}-$section",
        factory = viewModelFactory { initializer { HadithSectionViewModel(library, book, section, Dispatchers.IO) } },
    )
    val hadiths by viewModel.hadiths.collectAsState()
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    HadithListScreen("${bookTitle(book)} · ${numerals.apply(section.toString())}", hadiths, onBack)
}

@Composable
fun HadithSearchDestination(onBack: () -> Unit) {
    val library = appContainer().hadith
    val viewModel: HadithSearchViewModel = viewModel(
        factory = viewModelFactory { initializer { HadithSearchViewModel(library, Dispatchers.IO) } },
    )
    val state by viewModel.uiState.collectAsState()
    val books by library.books.collectAsState()
    val anyBook = books.values.any { it == BookState.Installed }
    HadithSearchScreen(state, anyBook, viewModel::onQueryChange, onBack)
}

@Composable
private fun bookTitle(book: HadithBook) =
    HadithTerms.title(book, LocalConfiguration.current.locales[0].language == "ar")
