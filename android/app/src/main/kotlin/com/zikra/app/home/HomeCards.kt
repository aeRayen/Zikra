package com.zikra.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.app.prayer.PrayerTimesSource
import com.zikra.app.prayer.PrayerTimesUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A card on «Today». Each implemented feature contributes its own (ADR-017); [key] is stable. */
sealed interface HomeCard {
    val key: String

    data class NextPrayer(val state: PrayerTimesUiState.Ready) : HomeCard {
        override val key = "prayer.next"
    }

    /** The last ayah read, to continue from (WP 2.12). */
    data class ContinueReading(val saved: com.zikra.app.quran.SavedAyah) : HomeCard {
        override val key = "quran.continue"
    }

    /** Prayer times are implemented but need a location first. */
    data object ChoosePrayerLocation : HomeCard {
        override val key = "prayer.location"
    }
}

/**
 * Contract for features that put cards on Home (WP 2.8). A feature that is not implemented has no
 * provider, so Home can never show a card that leads nowhere.
 */
fun interface HomeCardProvider {
    fun cards(language: Flow<String>): Flow<List<HomeCard>>
}

/** Prayer feature: the next prayer with its countdown, or a request to choose a location. */
class PrayerHomeCards(private val source: PrayerTimesSource) : HomeCardProvider {
    override fun cards(language: Flow<String>): Flow<List<HomeCard>> = source.states(language).map { state ->
        when (state) {
            is PrayerTimesUiState.Ready -> listOf(HomeCard.NextPrayer(state))
            PrayerTimesUiState.NoLocation -> listOf(HomeCard.ChoosePrayerLocation)
            PrayerTimesUiState.Loading -> emptyList()
        }
    }
}

data class HomeUiState(val loaded: Boolean = false, val cards: List<HomeCard> = emptyList())

class HomeViewModel(providers: List<HomeCardProvider>) : ViewModel() {
    private val language = MutableStateFlow("en")

    val uiState: StateFlow<HomeUiState> =
        cards(providers).stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    private fun cards(providers: List<HomeCardProvider>): Flow<HomeUiState> {
        if (providers.isEmpty()) return flowOf(HomeUiState(loaded = true))
        val perProvider = providers.map { it.cards(language) }
        return combine(perProvider) { lists -> HomeUiState(loaded = true, cards = lists.flatMap { it.toList() }) }
    }

    fun setLanguage(tag: String) {
        language.value = tag
    }
}
