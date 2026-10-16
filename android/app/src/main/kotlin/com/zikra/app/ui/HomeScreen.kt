package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeUiState
import com.zikra.app.quran.SavedAyah
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem

/**
 * «Today» (WP 2.8). Shows the cards of implemented features only (ADR-017); with none, it says so
 * honestly instead of showing sample data.
 */
@Composable
fun HomeScreen(
    state: HomeUiState = HomeUiState(loaded = true),
    onOpenPrayer: () -> Unit = {},
    onChooseLocation: () -> Unit = {},
    modifier: Modifier = Modifier,
    onContinueReading: (surah: Int, ayah: Int) -> Unit = { _, _ -> },
    onOpenAdhkar: (chapter: Int) -> Unit = {},
    onOpenMemorization: () -> Unit = {},
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZikraSpacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        if (state.loaded && state.cards.isEmpty()) {
            EmptyState(
                title = stringResource(R.string.home_empty_title),
                message = stringResource(R.string.home_empty_message),
            )
        }
        state.cards.forEach { card ->
            when (card) {
                is HomeCard.NextPrayer -> NextPrayerHomeCard(card, onOpenPrayer)
                HomeCard.ChoosePrayerLocation -> ChooseLocationHomeCard(onChooseLocation)
                is HomeCard.ContinueReading -> ContinueReadingCard(card.saved, card, onContinueReading)
                is HomeCard.DailyAdhkar -> DailyAdhkarHomeCard(card, onOpenAdhkar)
                is HomeCard.MemorizationToday -> MemorizationHomeCard(card, onOpenMemorization)
            }
        }
    }
}

@Composable
private fun NextPrayerHomeCard(card: HomeCard.NextPrayer, onOpen: () -> Unit) {
    val state = card.state
    val formatTime = rememberTimeFormatter()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {}
            .testTag(homeCardTag(card)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.prayer_next_label), style = MaterialTheme.typography.labelLarge)
            val template = if (state.nextIsTomorrow) R.string.prayer_next_tomorrow else R.string.prayer_next_at
            Text(
                text = stringResource(
                    template,
                    stringResource(state.next.labelRes()),
                    Bidi.isolate(formatTime(state.nextTime))
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(text = stringResource(R.string.prayer_remaining, remaining(state.minutesUntilNext)))
            Text(
                text = locationTitle(state),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun ChooseLocationHomeCard(onChooseLocation: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().testTag(homeCardTag(HomeCard.ChoosePrayerLocation))) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.prayer_no_location_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.prayer_no_location_message),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onChooseLocation) { Text(text = stringResource(R.string.prayer_choose_location)) }
        }
    }
}

/** Shared by Home and the Quran index: the last ayah read, tap to continue. */
@Composable
internal fun ContinueReadingCard(saved: SavedAyah, card: HomeCard?, onOpen: (Int, Int) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val numerals = NumeralSystem.forLanguage(language)
    val name = if (language == "ar") saved.surah.nameArabic else saved.surah.nameTransliterated
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(saved.surah.number, saved.ayah.ayah) }
            .semantics(mergeDescendants = true) {}
            .testTag(card?.let(::homeCardTag) ?: CONTINUE_READING_TAG),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.quran_continue), style = MaterialTheme.typography.labelLarge)
            Text(
                text = stringResource(
                    R.string.quran_continue_at,
                    Bidi.isolate(name),
                    numerals.apply(saved.ayah.ayah.toString())
                ),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DailyAdhkarHomeCard(card: HomeCard.DailyAdhkar, onOpen: (Int) -> Unit) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(card.chapter.id) }
            .semantics(mergeDescendants = true) {}
            .testTag(homeCardTag(card)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.adhkar_home_label), style = MaterialTheme.typography.labelLarge)
            Text(text = Bidi.isolate(displayTitle(card.chapter)), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(
                    R.string.adhkar_progress,
                    numerals.apply(card.done.toString()),
                    numerals.apply(card.chapter.size.toString()),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MemorizationHomeCard(card: HomeCard.MemorizationToday, onOpen: () -> Unit) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    OutlinedCard(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onOpen)
            .semantics(mergeDescendants = true) {}
            .testTag(homeCardTag(card)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.memorization_home_label), style = MaterialTheme.typography.labelLarge)
            Text(
                text = pluralStringResource(
                    R.plurals.memorization_due,
                    card.reviews,
                    numerals.apply(card.reviews.toString()),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            if (card.newPages > 0) {
                Text(
                    text = stringResource(R.string.memorization_new_today),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

internal const val CONTINUE_READING_TAG = "quran_continue"

internal fun homeCardTag(card: HomeCard) = "home_card_${card.key}"
