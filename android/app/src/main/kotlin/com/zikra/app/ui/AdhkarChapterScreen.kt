package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.app.adhkar.AdhkarChapterUiState
import com.zikra.core.adhkar.Dhikr
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem

/** One chapter: every dhikr with its counter and the author's reference (WP 2.14b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarChapterScreen(
    state: AdhkarChapterUiState,
    counts: Map<Int, Int>,
    actions: AdhkarChapterActions,
    modifier: Modifier = Modifier,
    saveFailed: Boolean = false,
) {
    Column(modifier.fillMaxSize()) {
        MediumTopAppBar(
            title = {
                val title = (state as? AdhkarChapterUiState.Ready)?.chapter?.title
                Text(text = title?.let(Bidi::isolate) ?: stringResource(R.string.adhkar_title))
            },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
            actions = {
                if (state is AdhkarChapterUiState.Ready) {
                    IconButton(onClick = actions.onReset, modifier = Modifier.testTag(ADHKAR_RESET_TAG)) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.adhkar_reset))
                    }
                }
            },
        )
        when (state) {
            AdhkarChapterUiState.Loading -> Unit
            AdhkarChapterUiState.Unavailable -> EmptyState(
                title = stringResource(R.string.adhkar_title),
                message = stringResource(R.string.adhkar_unavailable),
            )
            is AdhkarChapterUiState.Ready -> AdhkarList(state, counts, actions.onCount, saveFailed)
        }
    }
}

@Composable
private fun AdhkarList(
    state: AdhkarChapterUiState.Ready,
    counts: Map<Int, Int>,
    onCount: (Dhikr) -> Unit,
    saveFailed: Boolean,
) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    val done = state.adhkar.count { (counts[it.id] ?: 0) >= it.repeat }
    LazyColumn(
        Modifier.fillMaxSize().testTag(ADHKAR_LIST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text(
                text = stringResource(
                    R.string.adhkar_progress,
                    numerals.apply(done.toString()),
                    numerals.apply(state.adhkar.size.toString()),
                ),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = ZikraSpacing.screenGutter).testTag(ADHKAR_PROGRESS_TAG),
            )
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.adhkar_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = ZikraSpacing.screenGutter),
                )
            }
        }
        items(state.adhkar, key = { it.id }) { dhikr ->
            DhikrCard(dhikr, counts[dhikr.id] ?: 0, numerals, onCount)
        }
        item {
            Text(
                text = stringResource(R.string.adhkar_sources),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(ZikraSpacing.screenGutter),
            )
        }
    }
}

@Composable
private fun DhikrCard(dhikr: Dhikr, count: Int, numerals: NumeralSystem, onCount: (Dhikr) -> Unit) {
    val done = count >= dhikr.repeat
    val haptics = LocalHapticFeedback.current
    val countOnce = {
        val last = count + 1 >= dhikr.repeat
        haptics.performHapticFeedback(if (last) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove)
        onCount(dhikr)
    }
    val colors = MaterialTheme.colorScheme
    val container = if (done) colors.secondaryContainer else colors.surfaceContainerLow
    // The whole card counts too: a large target, used without looking (DESIGN_SYSTEM_AR.md).
    Card(
        onClick = countOnce,
        enabled = !done,
        colors = CardDefaults.cardColors(
            containerColor = container,
            disabledContainerColor = container,
            disabledContentColor = colors.onSurface,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZikraSpacing.screenGutter)
            .testTag(dhikrTag(dhikr.id)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = dhikrText(dhikr.parts, SpanStyle(fontFamily = QuranFont, fontSize = QURAN_SIZE_SP.sp)),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = TEXT_SIZE_SP.sp,
                        lineHeight = LINE_SP.sp,
                    ),
                    textAlign = TextAlign.Start,
                    color = colors.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val times = numerals.apply(dhikr.repeat.toString())
                Text(
                    text = pluralStringResource(R.plurals.adhkar_times, dhikr.repeat, times),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                CountButton(dhikr, count, numerals, countOnce)
            }
            References(dhikr)
        }
    }
}

/** The author's footnotes, folded until asked for (they can be long). */
@Composable
private fun References(dhikr: Dhikr) {
    var shown by rememberSaveable(dhikr.id) { mutableStateOf(false) }
    TextButton(onClick = { shown = !shown }, modifier = Modifier.testTag(referenceTag(dhikr.id))) {
        Text(text = stringResource(if (shown) R.string.adhkar_reference_hide else R.string.adhkar_reference_show))
    }
    if (shown) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxWidth().testTag(referenceTextTag(dhikr.id))) {
                dhikr.references.forEach {
                    Text(text = withoutMarker(it), style = MaterialTheme.typography.bodyMedium)
                }
                dhikr.notes.forEach {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CountButton(dhikr: Dhikr, count: Int, numerals: NumeralSystem, onCount: () -> Unit) {
    val said = numerals.apply(count.toString())
    val times = numerals.apply(dhikr.repeat.toString())
    val description = stringResource(R.string.adhkar_count_description, said, times)
    val done = count >= dhikr.repeat
    FilledTonalButton(
        onClick = onCount,
        enabled = !done,
        modifier = Modifier.semantics { contentDescription = description }.testTag(countTag(dhikr.id)),
    ) {
        if (done) {
            Text(text = stringResource(R.string.adhkar_done))
        } else {
            Text(text = Bidi.isolate("$said / $times"))
        }
    }
}

private const val TEXT_SIZE_SP = 20
private const val QURAN_SIZE_SP = 23
private const val LINE_SP = 38
