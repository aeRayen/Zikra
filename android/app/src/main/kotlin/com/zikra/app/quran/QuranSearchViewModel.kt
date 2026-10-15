package com.zikra.app.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.quran.Ayah
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.QuranText
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

data class QuranSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    /** null until a search has run for the current query. */
    val results: List<Pair<Surah, Ayah>>? = null,
    val total: Int = 0,
    val unavailable: Boolean = false,
)

/** Arabic search over the whole Quran (WP 2.11); results are the verbatim Uthmani ayahs. */
class QuranSearchViewModel(private val quran: () -> QuranText, private val io: CoroutineDispatcher) : ViewModel() {
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
                withContext(io) {
                    val text = quran()
                    val surahs = text.surahs().associateBy { it.number }
                    val found = text.search(query)
                    state.value.copy(results = found.ayahs.map { surahs.getValue(it.surah) to it }, total = found.total)
                }
            } catch (_: QuranIntegrityException) {
                state.value.copy(unavailable = true)
            }
            state.value = next.copy(searching = false)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
