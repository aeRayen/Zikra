package com.zikra.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.zikra.app.R
import com.zikra.app.quran.ReaderUiState

/** Reader top bar: surah name on its own line (never cut), text size, translation and night mode. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderTopBar(state: ReaderUiState, night: Boolean, showTranslation: Boolean, actions: ReaderActions) {
    MediumTopAppBar(
        title = {
            val surah = (state as? ReaderUiState.Ready)?.surah
            Text(
                text = surah?.let { titleOf(it.nameArabic, it.nameTransliterated) }.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
