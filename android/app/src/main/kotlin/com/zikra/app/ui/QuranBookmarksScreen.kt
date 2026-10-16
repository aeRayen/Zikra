package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.app.quran.SavedAyah
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem

/** Saved ayahs (WP 2.12), newest first; tap opens the reader, the bookmark button removes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranBookmarksScreen(
    bookmarks: List<SavedAyah>?,
    onOpen: (surah: Int, ayah: Int) -> Unit,
    onRemove: (SavedAyah) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.quran_bookmarks)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        when {
            bookmarks == null -> Unit
            bookmarks.isEmpty() -> EmptyState(
                title = stringResource(R.string.quran_bookmarks),
                message = stringResource(R.string.quran_bookmarks_empty),
            )
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(
                    bookmarks,
                    key = { it.surah.number * AYAH_KEY + it.ayah.ayah }
                ) { saved -> SavedRow(saved, onOpen, onRemove) }
            }
        }
    }
}

@Composable
private fun SavedRow(saved: SavedAyah, onOpen: (Int, Int) -> Unit, onRemove: (SavedAyah) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val numerals = NumeralSystem.forLanguage(language)
    val name = if (language == "ar") saved.surah.nameArabic else saved.surah.nameTransliterated
    val number = numerals.apply(saved.ayah.ayah.toString())
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(saved.surah.number, saved.ayah.ayah) }
            .padding(start = ZikraSpacing.screenGutter, top = 8.dp, bottom = 8.dp)
            .testTag(savedTag(saved.surah.number, saved.ayah.ayah)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.quran_search_result_ref, Bidi.isolate(name), number),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    text = saved.ayah.text,
                    fontFamily = QuranFont,
                    fontSize = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        IconButton(onClick = { onRemove(saved) }) {
            Icon(BookmarkFilledIcon, contentDescription = stringResource(R.string.reader_bookmark_remove, number))
        }
    }
    HorizontalDivider()
}

private const val AYAH_KEY = 1000
internal fun savedTag(surah: Int, ayah: Int) = "quran_saved_${surah}_$ayah"
