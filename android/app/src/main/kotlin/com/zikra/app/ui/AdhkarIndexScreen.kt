package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.adhkar.AdhkarIndexUiState
import com.zikra.core.adhkar.AdhkarChapter
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import kotlin.math.cos
import kotlin.math.sin

/** Prayer beads for the adhkar tab: a ring of beads and a tassel, drawn in code. */
val AdhkarTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "AdhkarTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(fill = SolidColor(Color.Black)) {
        fun bead(cx: Float, cy: Float, r: Float) {
            moveTo(cx - r, cy)
            arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = 2 * r, dy1 = 0f)
            arcToRelative(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, dx1 = -2 * r, dy1 = 0f)
            close()
        }
        // Eleven beads on a circle, the gap at the bottom where the tassel hangs.
        for (i in 1..BEADS) {
            val angle = Math.PI / 2 + i * 2 * Math.PI / (BEADS + 1)
            bead((12 + 7 * cos(angle)).toFloat(), (10 + 7 * sin(angle)).toFloat(), 1.5f)
        }
        bead(12f, 17.5f, 1.6f)
        moveTo(11.2f, 19f)
        lineTo(12.8f, 19f)
        lineTo(13.6f, 23f)
        lineTo(10.4f, 23f)
        close()
    }.build()
}

private const val BEADS = 11

/** Chapters of Hisn al-Muslim, the daily ones first (WP 2.14b). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarIndexScreen(
    state: AdhkarIndexUiState,
    onOpen: (chapter: Int) -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onReminders: () -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.adhkar_title)) },
            actions = {
                IconButton(onClick = onReminders, modifier = Modifier.testTag(ADHKAR_REMINDERS_BUTTON_TAG)) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = stringResource(R.string.adhkar_reminders_title),
                    )
                }
            },
        )
        when (state) {
            AdhkarIndexUiState.Loading -> Unit
            AdhkarIndexUiState.Unavailable -> EmptyState(
                title = stringResource(R.string.adhkar_title),
                message = stringResource(R.string.adhkar_unavailable),
            )
            is AdhkarIndexUiState.Ready -> ChapterList(state, onOpen, onQueryChange)
        }
    }
}

@Composable
private fun ChapterList(state: AdhkarIndexUiState.Ready, onOpen: (Int) -> Unit, onQueryChange: (String) -> Unit) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    LazyColumn(Modifier.fillMaxSize().testTag(ADHKAR_CHAPTERS_TAG)) {
        if (state.query.isEmpty()) {
            item { SectionTitle(stringResource(R.string.adhkar_daily)) }
            items(state.daily, key = { "daily-${it.id}" }) { chapter ->
                DailyChapterCard(chapter, numerals, onOpen)
            }
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text(text = stringResource(R.string.adhkar_search_hint)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ZikraSpacing.screenGutter, vertical = 8.dp)
                    .testTag(ADHKAR_SEARCH_TAG),
            )
        }
        item { SectionTitle(stringResource(R.string.adhkar_all)) }
        if (state.chapters.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.adhkar_no_match),
                    modifier = Modifier.padding(ZikraSpacing.screenGutter),
                )
            }
        }
        items(state.chapters, key = { it.id }) { chapter ->
            ListItem(
                leadingContent = {
                    Text(text = numerals.apply(chapter.id.toString()), style = MaterialTheme.typography.titleMedium)
                },
                overlineContent = chapter.translatedTitle?.let { { Text(text = Bidi.isolate(chapter.title)) } },
                headlineContent = { Text(text = Bidi.isolate(displayTitle(chapter))) },
                supportingContent = { Text(text = adhkarCount(chapter, numerals)) },
                modifier = Modifier
                    .clickable(role = Role.Button) { onOpen(chapter.id) }
                    .testTag(chapterTag(chapter.id)),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun DailyChapterCard(chapter: AdhkarChapter, numerals: NumeralSystem, onOpen: (Int) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 4.dp)
            .clickable(role = Role.Button) { onOpen(chapter.id) }
            .semantics(mergeDescendants = true) {}
            .testTag(dailyChapterTag(chapter.id)),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = Bidi.isolate(displayTitle(chapter)), style = MaterialTheme.typography.titleMedium)
            if (chapter.translatedTitle != null) {
                Text(text = Bidi.isolate(chapter.title), style = MaterialTheme.typography.bodyMedium)
            }
            Text(text = adhkarCount(chapter, numerals), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun adhkarCount(chapter: AdhkarChapter, numerals: NumeralSystem) =
    pluralStringResource(R.plurals.adhkar_count, chapter.size, numerals.apply(chapter.size.toString()))

internal const val ADHKAR_CHAPTERS_TAG = "adhkar_chapters"
internal const val ADHKAR_SEARCH_TAG = "adhkar_search"
internal const val ADHKAR_REMINDERS_BUTTON_TAG = "adhkar_reminders_button"
internal fun chapterTag(chapter: Int) = "adhkar_chapter_$chapter"
internal fun dailyChapterTag(chapter: Int) = "adhkar_daily_$chapter"
