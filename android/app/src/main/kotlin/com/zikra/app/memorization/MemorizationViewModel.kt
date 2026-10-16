package com.zikra.app.memorization

import android.database.SQLException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.Memorization
import com.zikra.core.memorization.MemorizationPlan
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.QuranText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** How much of one juz' is memorized. */
data class JuzProgress(val juz: Int, val memorized: Int, val pages: Int)

data class MemorizationUiState(
    val loaded: Boolean = false,
    /** The bundled Quran text failed its check: nothing to memorize from. */
    val unavailable: Boolean = false,
    val plan: MemorizationPlan? = null,
    val newPages: List<MushafPage> = emptyList(),
    val reviews: List<MushafPage> = emptyList(),
    val memorized: Int = 0,
    val juz: List<JuzProgress> = emptyList(),
)

/** Memorization plan, today's new pages and reviews, progress by juz' (WP 3.9). */
class MemorizationViewModel(
    private val data: MemorizationData,
    private val quran: () -> QuranText,
    private val io: CoroutineDispatcher,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val pages = MutableStateFlow<List<MushafPage>?>(null)
    private val unavailable = MutableStateFlow(false)
    private val failed = MutableStateFlow(false)

    /** True when the last change could not be saved. */
    val saveFailed: StateFlow<Boolean> = failed.asStateFlow()

    val uiState: StateFlow<MemorizationUiState> = combine(pages, data.state, unavailable) { mushaf, state, broken ->
        when {
            broken -> MemorizationUiState(loaded = true, unavailable = true)
            mushaf == null -> MemorizationUiState()
            else -> {
                val agenda = Memorization.agenda(state.plan, state.pages, today())
                val byPage = mushaf.associateBy { it.page }
                val done = state.pages.mapTo(HashSet()) { it.page }
                MemorizationUiState(
                    loaded = true,
                    plan = state.plan,
                    newPages = agenda.newPages.mapNotNull(byPage::get),
                    reviews = agenda.reviews.mapNotNull { byPage[it.page] },
                    memorized = done.size,
                    juz = mushaf.groupBy { it.juz }.toSortedMap().map { (juz, list) ->
                        JuzProgress(juz, list.count { it.page in done }, list.size)
                    },
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MemorizationUiState())

    init {
        viewModelScope.launch {
            try {
                pages.value = withContext(io) { mushafPages(quran()) }
            } catch (_: QuranIntegrityException) {
                unavailable.value = true
            }
        }
    }

    fun setPlan(direction: Direction, pace: Pace) = save { data.setPlan(MemorizationPlan(direction, pace)) }

    fun memorized(page: Int) = save { data.memorized(page, today()) }

    fun reviewed(page: Int, recall: Recall) = save { data.reviewed(page, recall, today()) }

    /** Marks every page of [juz] as memorized before the plan (pages already tracked keep their state). */
    fun memorizedJuz(juz: Int) {
        val inJuz = pages.value?.filter { it.juz == juz }?.map { it.page } ?: return
        save {
            val tracked = data.state.first().pages.mapTo(HashSet()) { it.page }
            data.alreadyMemorized(inJuz, today(), tracked)
        }
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            failed.value = try {
                withContext(io) { block() }
                false
            } catch (_: SQLException) {
                true // e.g. the phone's storage is full
            } catch (_: IOException) {
                true
            }
        }
    }
}
