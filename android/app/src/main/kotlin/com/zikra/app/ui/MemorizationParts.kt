package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.memorization.JuzProgress
import com.zikra.app.memorization.MushafPage
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.MemorizationPlan
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall

@Composable
internal fun rememberNumerals(): NumeralSystem =
    NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)

/** «Page 604 · an-Nas» in the UI language. */
@Composable
internal fun pageLabel(page: MushafPage): String {
    val language = LocalConfiguration.current.locales[0].language
    val name = if (language == "ar") page.surahNameArabic else page.surahNameLatin
    val number = rememberNumerals().apply(page.page.toString())
    return stringResource(R.string.memorization_page, number, Bidi.isolate(name))
}

/** Direction and pace; [current] null means a new plan. */
@Composable
internal fun PlanCard(current: MemorizationPlan?, onSave: (Direction, Pace) -> Unit) {
    var direction by rememberSaveable { mutableStateOf(current?.direction ?: Direction.FROM_END) }
    var pace by rememberSaveable { mutableStateOf(current?.pace ?: Pace.ONE_PAGE) }
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter).testTag(PLAN_CARD_TAG)) {
        Column(Modifier.padding(ZikraSpacing.screenGutter).selectableGroup()) {
            Text(text = stringResource(R.string.memorization_plan), style = MaterialTheme.typography.titleMedium)
            Choice(stringResource(R.string.memorization_direction_end), direction == Direction.FROM_END) {
                direction = Direction.FROM_END
            }
            Choice(stringResource(R.string.memorization_direction_start), direction == Direction.FROM_START) {
                direction = Direction.FROM_START
            }
            Spacer(Modifier.padding(4.dp))
            Pace.entries.forEach { p ->
                Choice(stringResource(p.labelRes()), pace == p, Modifier.testTag(paceTag(p))) { pace = p }
            }
            val changed = current == null || current.direction != direction || current.pace != pace
            Button(
                onClick = { onSave(direction, pace) },
                enabled = changed,
                modifier = Modifier.padding(top = 8.dp).testTag(PLAN_SAVE_TAG),
            ) {
                val label = if (current == null) R.string.memorization_start else R.string.memorization_save_plan
                Text(text = stringResource(label))
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun Pace.labelRes() = when (this) {
    Pace.HALF_PAGE -> R.string.memorization_pace_half
    Pace.ONE_PAGE -> R.string.memorization_pace_one
    Pace.TWO_PAGES -> R.string.memorization_pace_two
}

/** A new page for today: open it in the reader, then mark it memorized. */
@Composable
internal fun NewPageRow(page: MushafPage, onOpen: (Int, Int) -> Unit, onMemorized: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter).testTag(newPageTag(page.page)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = pageLabel(page), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        OpenButton(page, onOpen)
        FilledTonalButton(onClick = { onMemorized(page.page) }, modifier = Modifier.testTag(memorizedTag(page.page))) {
            Text(text = stringResource(R.string.memorization_done))
        }
    }
}

/** A page due for review: open it, recite it, then say how it went (the next review follows from it). */
@Composable
internal fun ReviewRow(page: MushafPage, onOpen: (Int, Int) -> Unit, onReviewed: (Int, Recall) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter).testTag(reviewTag(page.page))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = pageLabel(page), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            OpenButton(page, onOpen)
        }
        // Four equal buttons on one line: easy to reach without looking for them.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Recall.entries.forEach { recall ->
                OutlinedButton(
                    onClick = { onReviewed(page.page, recall) },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier = Modifier.weight(1f).testTag(recallTag(page.page, recall)),
                ) { Text(text = stringResource(recall.labelRes()), maxLines = 1) }
            }
        }
    }
}

private fun Recall.labelRes() = when (this) {
    Recall.FORGOT -> R.string.recall_forgot
    Recall.HARD -> R.string.recall_hard
    Recall.GOOD -> R.string.recall_good
    Recall.EASY -> R.string.recall_easy
}

/** One juz' with its progress; marks it memorized-before when not complete. */
@Composable
internal fun JuzRow(juz: JuzProgress, onMemorizedJuz: (Int) -> Unit) {
    val numerals = rememberNumerals()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter).testTag(juzTag(juz.juz)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = stringResource(R.string.memorization_juz, numerals.apply(juz.juz.toString())))
            Text(
                text = stringResource(
                    R.string.memorization_juz_progress,
                    numerals.apply(juz.memorized.toString()),
                    numerals.apply(juz.pages.toString()),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (juz.memorized < juz.pages) {
            TextButton(onClick = { onMemorizedJuz(juz.juz) }, modifier = Modifier.testTag(markJuzTag(juz.juz))) {
                Text(text = stringResource(R.string.memorization_mark_juz))
            }
        }
    }
}

@Composable
private fun OpenButton(page: MushafPage, onOpen: (Int, Int) -> Unit) {
    TextButton(onClick = { onOpen(page.surah, page.ayah) }) { Text(text = stringResource(R.string.memorization_open)) }
}
