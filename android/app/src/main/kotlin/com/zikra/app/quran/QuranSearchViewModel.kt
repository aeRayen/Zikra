package com.zikra.app.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.quran.Ayah
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.Surah
import com.zikra.core.quran.search
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One match: the verbatim Arabic ayah, plus the translation when the match was in it. */
data class SearchHit(val surah: Surah, val ayah: Ayah, val translation: String? = null)

data class QuranSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    /** null until a search has run for the current query. */
    val results: List<SearchHit>? = null,
    val total: Int = 0,
    val unavailable: Boolean = false,
)

/**
 * Search over the whole Quran (WP 2.11, 2.13): Arabic letters search the Arabic text; Latin letters
 * search the translation of the meanings. Results always show the verbatim Uthmani ayah.
 */
class QuranSearchViewModel(private val content: QuranContent, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow(QuranSearchUiState())
    val uiState: StateFlow<QuranSearchUiState> = state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(query: String) {
        state.update { it.copy(query = query, results = null, total = 0) }
        job?.cancel()
        if (query.isBlank()) return
        job = viewModelScope.launch {
            state.update { it.copy(searching = true) }
            delay(DEBOUNCE_MS)
            val next = try {
                withContext(io) { search(query) }
            } catch (_: QuranIntegrityException) {
                state.value.copy(unavailable = true)
            }
            state.value = next.copy(searching = false)
        }
    }

    private fun search(query: String): QuranSearchUiState {
        val text = content.text()
        val surahs = text.surahs().associateBy { it.number }
        val translation = content.translation()
        if (translation != null && query.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            val found = translation.search(query)
            val ayahsBySurah = found.map { it.surah }.distinct().associateWith { s ->
                text.ayahs(
                    s
                ).associateBy { it.ayah }
            }
            val hits = found.mapNotNull { t ->
                ayahsBySurah[t.surah]?.get(t.ayah)?.let { SearchHit(surahs.getValue(t.surah), it, t.text) }
            }
            return state.value.copy(results = hits, total = hits.size)
        }
        val found = text.search(query)
        return state.value.copy(
            results = found.ayahs.map { SearchHit(surahs.getValue(it.surah), it) },
            total = found.total
        )
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
