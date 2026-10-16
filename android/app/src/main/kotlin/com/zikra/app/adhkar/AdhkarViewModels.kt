package com.zikra.app.adhkar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.adhkar.AdhkarBook
import com.zikra.core.adhkar.AdhkarChapter
import com.zikra.core.adhkar.AdhkarIntegrityException
import com.zikra.core.adhkar.AdhkarSourceInfo
import com.zikra.core.adhkar.Dhikr
import com.zikra.core.i18n.SearchNormalizer
import com.zikra.core.settings.AdhkarProgressStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface AdhkarIndexUiState {
    data object Loading : AdhkarIndexUiState

    /** The bundled adhkar failed their check: nothing is shown rather than an incomplete book. */
    data object Unavailable : AdhkarIndexUiState

    data class Ready(
        /** Morning and evening, after prayer, sleep, waking: offered first. */
        val daily: List<AdhkarChapter>,
        /** Every chapter, or those whose title matches the query. */
        val chapters: List<AdhkarChapter>,
        val query: String = "",
    ) : AdhkarIndexUiState
}

/** Chapters of Hisn al-Muslim (WP 2.14b). */
class AdhkarIndexViewModel(private val book: () -> AdhkarBook, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow<AdhkarIndexUiState>(AdhkarIndexUiState.Loading)
    val uiState: StateFlow<AdhkarIndexUiState> = state.asStateFlow()
    private var all: List<AdhkarChapter> = emptyList()

    init {
        viewModelScope.launch {
            state.value = try {
                all = withContext(io) { book().chapters() }
                val byId = all.associateBy { it.id }
                AdhkarIndexUiState.Ready(AdhkarBook.DAILY.mapNotNull { byId[it] }, all)
            } catch (_: AdhkarIntegrityException) {
                AdhkarIndexUiState.Unavailable
            }
        }
    }

    /** Filters chapters by title; tashkeel, hamza forms and case are ignored. */
    fun onQueryChange(query: String) {
        val ready = state.value as? AdhkarIndexUiState.Ready ?: return
        val key = SearchNormalizer.normalize(query)
        val matching = if (key.isEmpty()) all else all.filter { SearchNormalizer.normalize(it.title).contains(key) }
        state.value = ready.copy(chapters = matching, query = query)
    }
}

sealed interface AdhkarChapterUiState {
    data object Loading : AdhkarChapterUiState

    data object Unavailable : AdhkarChapterUiState

    data class Ready(val chapter: AdhkarChapter, val adhkar: List<Dhikr>, val info: AdhkarSourceInfo) :
        AdhkarChapterUiState
}

/** One chapter with today's counters, saved on every tap (WP 2.14b). */
class AdhkarChapterViewModel(
    private val book: () -> AdhkarBook,
    private val progress: AdhkarProgressStore,
    private val io: CoroutineDispatcher,
    private val chapterId: Int,
) : ViewModel() {
    private val state = MutableStateFlow<AdhkarChapterUiState>(AdhkarChapterUiState.Loading)
    val uiState: StateFlow<AdhkarChapterUiState> = state.asStateFlow()

    /** Times said today, by dhikr id. */
    val counts: StateFlow<Map<Int, Int>> = progress.counts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val failed = MutableStateFlow(false)

    /** True when the last tap could not be saved (the count shown may be lost on closing). */
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = try {
                withContext(io) {
                    val b = book()
                    val chapter = b.chapters().firstOrNull { it.id == chapterId }
                    if (chapter == null) {
                        AdhkarChapterUiState.Unavailable
                    } else {
                        AdhkarChapterUiState.Ready(chapter, b.chapter(chapterId), b.info)
                    }
                }
            } catch (_: AdhkarIntegrityException) {
                AdhkarChapterUiState.Unavailable
            }
        }
    }

    fun count(dhikr: Dhikr) = save { progress.count(dhikr.id, dhikr.repeat) }

    /** Starts this chapter again from zero. */
    fun reset() {
        val ready = state.value as? AdhkarChapterUiState.Ready ?: return
        save { progress.reset(ready.adhkar.map { it.id }) }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            failed.value = try {
                block()
                false
            } catch (_: IOException) {
                true
            }
        }
    }
}
