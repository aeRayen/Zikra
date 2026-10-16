package com.zikra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.app.quran.ReaderUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.designsystem.theme.ZikraTheme
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.quran.Ayah
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * One surah (WP 2.10). The Quran text is shown VERBATIM from the bundled Tanzil source, always
 * right-to-left in the mushaf font, whatever the UI language; ayah numbers are added as end-of-ayah
 * marks (U+06DD) with Arabic-Indic digits, outside the text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranReaderScreen(
    state: ReaderUiState,
    textScale: Float,
    night: Boolean,
    actions: ReaderActions,
    bookmarks: Set<Int> = emptySet(),
    showTranslation: Boolean = false,
) {
    ZikraTheme(darkTheme = night) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            TopAppBar(
                title = {
                    val surah = (state as? ReaderUiState.Ready)?.surah
                    Text(text = surah?.let { titleOf(it.nameArabic, it.nameTransliterated) }.orEmpty())
                },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_back)
                        )
                    }
                },
                actions = {
                    SizeButton(
                        stringResource(R.string.reader_smaller_glyph),
                        stringResource(R.string.reader_smaller),
                        READER_SMALLER_TAG,
                        actions.onSmaller
                    )
                    SizeButton(
                        stringResource(R.string.reader_bigger_glyph),
                        stringResource(R.string.reader_bigger),
                        READER_BIGGER_TAG,
                        actions.onBigger
                    )
                    if ((state as? ReaderUiState.Ready)?.translationInfo != null) {
                        val label =
                            if (showTranslation) R.string.reader_translation_hide else R.string.reader_translation_show
                        IconButton(
                            onClick = { actions.onToggleTranslation(showTranslation) },
                            modifier = Modifier.testTag(READER_TRANSLATION_TAG),
                        ) { Icon(TranslateIcon, contentDescription = stringResource(label)) }
                    }
                    IconButton(
                        onClick = { actions.onToggleNight(night) },
                        modifier = Modifier.testTag(READER_NIGHT_TAG)
                    ) {
                        Icon(PrayerTabIcon, contentDescription = stringResource(R.string.reader_night))
                    }
                },
            )
            when (state) {
                ReaderUiState.Loading -> Unit
                ReaderUiState.Unavailable -> EmptyState(
                    title = stringResource(R.string.quran_title),
                    message = stringResource(R.string.quran_unavailable),
                )
                is ReaderUiState.Ready -> AyahList(state, textScale, bookmarks, actions, showTranslation)
            }
        }
    }
}

/** «A−» / «A+» are visual; TalkBack reads [spoken]. */
@Composable
private fun SizeButton(glyph: String, spoken: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag).semantics { contentDescription = spoken }) {
        Text(text = glyph, modifier = Modifier.clearAndSetSemantics {})
    }
}

@Composable
private fun titleOf(arabic: String, transliterated: String): String =
    if (LocalConfiguration.current.locales[0].language == "ar") arabic else transliterated

@Composable
private fun AyahList(
    state: ReaderUiState.Ready,
    textScale: Float,
    bookmarks: Set<Int>,
    actions: ReaderActions,
    showTranslation: Boolean,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.startIndex)
    // «Continue reading»: report the ayah at the top whenever scrolling settles on a new one.
    LaunchedEffect(listState, state) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> state.ayahs.getOrNull(index)?.let { actions.onRead(it.ayah) } }
    }
    val uiNumerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    // The Quran is Arabic: right-to-left even when the app is in English.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        LazyColumn(Modifier.fillMaxSize().testTag(AYAH_LIST_TAG), state = listState) {
            if (showTranslation) {
                state.translationInfo?.let { info ->
                    item(
                        key = "credit"
                    ) { TranslationCredit(info.title) }
                }
            }
            itemsIndexed(state.ayahs, key = { _, a -> a.ayah }) { index, ayah ->
                val previous = state.ayahs.getOrNull(index - 1)
                if (previous == null || previous.page != ayah.page) PageMarker(ayah, uiNumerals)
                AyahRow(ayah, textScale, ayah.ayah in bookmarks, actions.onToggleBookmark)
                if (showTranslation) state.translation[ayah.ayah]?.let { TranslationText(it, textScale) }
            }
        }
    }
}

@Composable
private fun PageMarker(ayah: Ayah, numerals: NumeralSystem) {
    val page = stringResource(R.string.quran_page_label, numerals.apply(ayah.page.toString()))
    val juz = stringResource(R.string.quran_juz_label, numerals.apply(ayah.juz.toString()))
    Column(Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter, vertical = 8.dp)) {
        HorizontalDivider()
        Text(
            text = "$page · $juz",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun AyahRow(ayah: Ayah, textScale: Float, bookmarked: Boolean, onToggle: (Int) -> Unit) {
    val number = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language).apply(ayah.ayah.toString())
    val label =
        stringResource(if (bookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add, number)
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.weight(1f)) { AyahText(ayah, textScale) }
        IconButton(onClick = { onToggle(ayah.ayah) }, modifier = Modifier.testTag(bookmarkTag(ayah.ayah))) {
            Icon(
                if (bookmarked) BookmarkFilledIcon else BookmarkOutlineIcon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.let { if (bookmarked) it.primary else it.onSurfaceVariant },
            )
        }
    }
}

@Composable
private fun AyahText(ayah: Ayah, textScale: Float) {
    Text(
        // Verbatim text, then the end-of-ayah mark with the number (Arabic-Indic digits, as in the mushaf).
        text = ayah.text + " " + END_OF_AYAH + NumeralSystem.ARAB.apply(ayah.ayah.toString()),
        fontFamily = QuranFont,
        fontSize = (BASE_SIZE_SP * textScale).sp,
        lineHeight = (BASE_SIZE_SP * textScale * LINE_HEIGHT).sp,
        textAlign = TextAlign.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 10.dp)
            .testTag(ayahTag(ayah.ayah)),
    )
}

private const val END_OF_AYAH = '۝'
private const val BASE_SIZE_SP = 24f
private const val LINE_HEIGHT = 1.9f
internal const val AYAH_LIST_TAG = "quran_ayah_list"
internal const val READER_SMALLER_TAG = "reader_smaller"
internal const val READER_BIGGER_TAG = "reader_bigger"
internal const val READER_NIGHT_TAG = "reader_night"
internal const val READER_TRANSLATION_TAG = "reader_translation"
internal fun translationTag(ayah: Int) = "quran_translation_$ayah"
internal fun ayahTag(ayah: Int) = "quran_ayah_$ayah"
internal fun bookmarkTag(ayah: Int) = "quran_bookmark_$ayah"
