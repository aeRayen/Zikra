package com.zikra.app.hadith

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.hadith.Hadith
import com.zikra.core.hadith.HadithBook
import com.zikra.core.hadith.HadithSection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The six books and whether each is on the phone (WP 3.4c). */
class HadithBooksViewModel(private val library: HadithLibrary) : ViewModel() {
    val books: StateFlow<Map<HadithBook, BookState>> = library.books

    init {
        viewModelScope.launch { library.refresh() }
    }

    fun download(book: HadithBook) {
        viewModelScope.launch { library.download(book) }
    }
}

/** null while loading, or when the book is not installed. */
class HadithChaptersViewModel(library: HadithLibrary, book: HadithBook, io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow<List<HadithSection>?>(null)
    val sections: StateFlow<List<HadithSection>?> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = withContext(io) { library.reader(book)?.sections() } }
    }
}

class HadithSectionViewModel(
    library: HadithLibrary,
    book: HadithBook,
    section: Int,
    io: CoroutineDispatcher,
) : ViewModel() {
    private val state = MutableStateFlow<List<Hadith>?>(null)
    val hadiths: StateFlow<List<Hadith>?> = state.asStateFlow()

    init {
        viewModelScope.launch { state.value = withContext(io) { library.reader(book)?.section(section) } }
    }
}

data class HadithSearchUiState(
    val query: String = "",
    val results: List<Hadith> = emptyList(),
    val searching: Boolean = false,
)

/** Searches every installed book, as the user types (debounced). */
class HadithSearchViewModel(private val library: HadithLibrary, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(HadithSearchUiState())
    val uiState: StateFlow<HadithSearchUiState> = state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) {
        state.value = state.value.copy(query = query, searching = true)
        job?.cancel()
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val results = withContext(io) {
                HadithBook.entries.flatMap { book -> library.reader(book)?.search(query).orEmpty() }
            }
            state.value = state.value.copy(results = results, searching = false)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
