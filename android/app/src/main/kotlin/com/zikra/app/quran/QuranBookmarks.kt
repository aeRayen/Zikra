package com.zikra.app.quran

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeCardProvider
import com.zikra.core.quran.Ayah
import com.zikra.core.quran.QuranIntegrityException
import com.zikra.core.quran.QuranText
import com.zikra.core.quran.Surah
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A saved ayah with what is needed to show it. */
data class SavedAyah(val surah: Surah, val ayah: Ayah)

/** Resolves an ayah reference against the bundled text; null if the text is unavailable or the ref is stale. */
private fun QuranText.resolve(ref: AyahRef): SavedAyah? {
    val surah = surah(ref.surah)
    val ayah = surah?.let { ayahs(ref.surah).firstOrNull { it.ayah == ref.ayah } }
    return if (surah != null && ayah != null) SavedAyah(surah, ayah) else null
}

private fun safely(block: () -> SavedAyah?): SavedAyah? = try {
    block()
} catch (_: QuranIntegrityException) {
    null
}

/** «Continue reading» for the Quran index and Home (WP 2.12). */
fun continueReading(userData: QuranUserData, quran: () -> QuranText, io: CoroutineDispatcher): Flow<SavedAyah?> =
    userData.lastRead.map { ref -> ref?.let { safely { quran().resolve(it) } } }.flowOn(io)

/** Bookmarks list (WP 2.12), newest first. */
class QuranBookmarksViewModel(
    private val userData: QuranUserData,
    quran: () -> QuranText,
    io: CoroutineDispatcher,
) : ViewModel() {
    /** null while loading. */
    val bookmarks: StateFlow<List<SavedAyah>?> = userData.bookmarked
        .map { refs -> refs.mapNotNull { ref -> safely { quran().resolve(ref) } } }
        .flowOn(io)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun remove(saved: SavedAyah) {
        viewModelScope.launch { userData.toggle(AyahRef(saved.surah.number, saved.ayah.ayah)) }
    }
}

/** Home: the «continue reading» card once the user has read something (no card before). */
class QuranHomeCards(
    private val userData: QuranUserData,
    private val quran: () -> QuranText,
    private val io: CoroutineDispatcher,
) : HomeCardProvider {
    override fun cards(language: Flow<String>): Flow<List<HomeCard>> =
        continueReading(userData, quran, io).map { saved -> listOfNotNull(saved?.let { HomeCard.ContinueReading(it) }) }
}
