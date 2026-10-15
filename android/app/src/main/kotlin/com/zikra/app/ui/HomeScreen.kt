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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi

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

internal fun homeCardTag(card: HomeCard) = "home_card_${card.key}"
