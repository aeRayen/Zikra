package com.zikra.app.hadith

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.hadith.HadithBook
import com.zikra.core.hadith.HadithCatalog
import com.zikra.core.hadith.HadithPackCheck
import com.zikra.core.hadith.HadithReader
import com.zikra.core.packs.PackInstaller
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Where a book stands on this phone. */
sealed interface BookState {
    data object NotDownloaded : BookState

    data object Downloading : BookState

    data object Installed : BookState

    /** The last download failed: offline, or a pack that did not pass its checks (nothing was installed). */
    data class Failed(val error: AppError) : BookState
}

/**
 * The six books on this phone (WP 3.4c): downloaded on request as signed packs, verified and installed
 * by [PackInstaller] (ADR-046), then read offline. Readers stay open for the life of the process.
 */
class HadithLibrary(private val installer: () -> PackInstaller, private val io: CoroutineDispatcher) {
    private val states = MutableStateFlow<Map<HadithBook, BookState>>(emptyMap())
    private val readers = mutableMapOf<HadithBook, HadithReader>()

    val books: StateFlow<Map<HadithBook, BookState>> = states.asStateFlow()

    /** Reads what is installed (call once from a coroutine). */
    suspend fun refresh() {
        val installed = withContext(io) {
            HadithBook.entries.associateWith { installer().file(it.editionCode) != null }
        }
        states.update { current ->
            HadithBook.entries.associateWith { book ->
                when {
                    installed.getValue(book) -> BookState.Installed
                    current[book] == BookState.Downloading -> BookState.Downloading
                    current[book] is BookState.Failed -> current.getValue(book)
                    else -> BookState.NotDownloaded
                }
            }
        }
    }

    suspend fun download(book: HadithBook) {
        states.update { it + (book to BookState.Downloading) }
        val result = withContext(io) { installer().install(HadithCatalog.urls(book), HadithPackCheck) }
        val state = when (result) {
            is AppResult.Success -> {
                synchronized(readers) { readers.remove(book)?.close() } // a newer version: reopen
                BookState.Installed
            }
            is AppResult.Failure -> BookState.Failed(result.error)
        }
        states.update { it + (book to state) }
    }

    /** The installed book, or null. Disk I/O: call from [io]. */
    fun reader(book: HadithBook): HadithReader? = synchronized(readers) {
        readers[book]
            ?: installer().file(book.editionCode)?.let { HadithReader.open(book, it) }?.also { readers[book] = it }
    }
}
