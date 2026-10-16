package com.zikra.app.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.quran.Ayah
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.QuranText
import com.zikra.core.quran.Surah
import com.zikra.core.settings.ReaderPreferences
import com.zikra.core.settings.ReaderPreferencesStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** Where a juz starts, for the juz index. */
data class JuzStart(val juz: Int, val surah: Surah, val ayah: Int)

sealed interface QuranIndexUiState {
    data object Loading : QuranIndexUiState

    /** The bundled text failed its structure check: nothing is shown rather than a damaged Quran. */
    data object Unavailable : QuranIndexUiState

    data class Ready(val surahs: List<Surah>, val juz: List<JuzStart>) : QuranIndexUiState
}

/** Surah and juz index of the bundled Quran text (WP 2.10). */
class QuranIndexViewModel(private val quran: () -> QuranText, private val io: CoroutineDispatcher) : ViewModel() {
    private val state = MutableStateFlow<QuranIndexUiState>(QuranIndexUiState.Loading)
    val uiState: StateFlow<QuranIndexUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            state.value = try {
                withContext(io) {
                    val text = quran()
                    val surahs = text.surahs()
                    val byNumber = surahs.associateBy { it.number }
                    val juz = (1..QuranText.JUZ_COUNT).map { j ->
                        val first = text.juz(j).first()
                        JuzStart(j, byNumber.getValue(first.surah), first.ayah)
                    }
                    QuranIndexUiState.Ready(surahs, juz)
                }
            } catch (_: QuranIntegrityException) {
                QuranIndexUiState.Unavailable
            }
        }
    }
}

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data object Unavailable : ReaderUiState

    data class Ready(val surah: Surah, val ayahs: List<Ayah>, val startIndex: Int) : ReaderUiState
}

/** One surah, verbatim, starting at [startAyah] (WP 2.10). */
class QuranReaderViewModel(
    private val quran: () -> QuranText,
    private val preferences: ReaderPreferencesStore,
    private val userData: QuranUserData,
    private val io: CoroutineDispatcher,
    private val surahNumber: Int,
    startAyah: Int,
) : ViewModel() {
    private val state = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
    val uiState: StateFlow<ReaderUiState> = state.asStateFlow()

    val display: StateFlow<ReaderPreferences> =
        preferences.data.stateIn(viewModelScope, SharingStarted.Eagerly, ReaderPreferences())

    /** Bookmarked ayah numbers of this surah. */
    val bookmarks: StateFlow<Set<Int>> = userData.bookmarked
        .map { refs -> refs.filter { it.surah == surahNumber }.mapTo(HashSet()) { it.ayah } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private var saveJob: Job? = null

    fun toggleBookmark(ayah: Int) {
        viewModelScope.launch { withContext(io) { userData.toggle(AyahRef(surahNumber, ayah)) } }
    }

    /** Remembers the ayah at the top of the screen, at most once per [SAVE_DELAY_MS] of scrolling. */
    fun onRead(ayah: Int) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY_MS)
            withContext(io) { userData.saveLastRead(AyahRef(surahNumber, ayah)) }
        }
    }

    init {
        viewModelScope.launch {
            state.value = try {
                withContext(io) {
                    val text = quran()
                    val surah = text.surah(surahNumber)
                    if (surah == null) {
                        ReaderUiState.Unavailable
                    } else {
                        val ayahs = text.ayahs(surah.number)
                        ReaderUiState.Ready(surah, ayahs, (startAyah - 1).coerceIn(0, ayahs.lastIndex))
                    }
                }
            } catch (_: QuranIntegrityException) {
                ReaderUiState.Unavailable
            }
        }
    }

    fun bigger() = scaleBy(ReaderPreferences.STEP)

    fun smaller() = scaleBy(-ReaderPreferences.STEP)

    /** Night on/off; the first toggle leaves «follow the phone». */
    fun toggleNight(currentlyNight: Boolean) = edit { it.copy(night = !currentlyNight) }

    private fun scaleBy(delta: Float) = edit {
        val next = ((it.textScale + delta) * STEPS_PER_UNIT).roundToInt() / STEPS_PER_UNIT.toFloat()
        it.copy(textScale = next.coerceIn(ReaderPreferences.MIN_SCALE, ReaderPreferences.MAX_SCALE))
    }

    private fun edit(transform: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch {
            try {
                preferences.update(transform)
            } catch (_: IOException) {
                Unit // display preference only: keep the current look
            }
        }
    }

    private companion object {
        /** Rounds scales to one decimal so repeated taps never drift (1.0 -> 1.1 -> ... exactly). */
        const val STEPS_PER_UNIT = 10
        const val SAVE_DELAY_MS = 800L
    }
}
