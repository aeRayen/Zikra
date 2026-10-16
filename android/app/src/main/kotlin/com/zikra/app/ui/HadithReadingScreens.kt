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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.hadith.HadithSearchUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.hadith.Hadith
import com.zikra.core.hadith.HadithSection
import com.zikra.core.i18n.NumeralSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HadithTopBar(title: String, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
            }
        },
    )
}

/** The chapters of one book; titles are in English in the source (ADR-046), so each also has its number. */
@Composable
fun HadithChaptersScreen(
    title: String,
    sections: List<HadithSection>?,
    onOpen: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    Column(modifier.fillMaxSize()) {
        HadithTopBar(title, onBack)
        if (sections != null) {
            LazyColumn(Modifier.fillMaxSize().testTag(HADITH_CHAPTERS_TAG)) {
                item {
                    Text(
                        text = stringResource(R.string.hadith_titles_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ZikraSpacing.screenGutter, vertical = 4.dp),
                    )
                }
                items(sections, key = { it.id }) { section ->
                    ListItem(
                        overlineContent = {
                            Text(text = stringResource(R.string.hadith_chapter, numerals.apply(section.id.toString())))
                        },
                        headlineContent = { Text(text = section.titleEnglish) },
                        supportingContent = {
                            val count = numerals.apply(section.size.toString())
                            Text(text = pluralStringResource(R.plurals.hadith_count, section.size, count))
                        },
                        modifier = Modifier
                            .clickable(role = Role.Button) { onOpen(section.id) }
                            .testTag(sectionTag(section.id)),
                    )
                }
            }
        }
    }
}

/** The hadiths of one chapter, in book order. */
@Composable
fun HadithListScreen(title: String, hadiths: List<Hadith>?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        HadithTopBar(title, onBack)
        if (hadiths != null) {
            LazyColumn(
                Modifier.fillMaxSize().testTag(HADITH_LIST_TAG),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(hadiths, key = { "${it.book.key}-${it.number}" }) { HadithCard(it) }
            }
        }
    }
}

/** Search across every downloaded book (normalized: tashkeel and hamza forms are ignored). */
@Composable
fun HadithSearchScreen(
    state: HadithSearchUiState,
    anyBook: Boolean,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        HadithTopBar(stringResource(R.string.hadith_search), onBack)
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            enabled = anyBook,
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(text = stringResource(R.string.hadith_search_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZikraSpacing.screenGutter)
                .testTag(HADITH_SEARCH_FIELD_TAG),
        )
        val note = when {
            !anyBook -> R.string.hadith_search_needs_books
            state.query.length >= 2 && !state.searching && state.results.isEmpty() -> R.string.hadith_search_none
            else -> null
        }
        note?.let { Text(text = stringResource(it), modifier = Modifier.padding(ZikraSpacing.screenGutter)) }
        LazyColumn(
            Modifier.fillMaxSize().padding(top = 8.dp).testTag(HADITH_LIST_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.results, key = { "${it.book.key}-${it.number}" }) { HadithCard(it, showBook = true) }
        }
    }
}

internal const val HADITH_CHAPTERS_TAG = "hadith_chapters"
internal const val HADITH_LIST_TAG = "hadith_list"
internal const val HADITH_SEARCH_FIELD_TAG = "hadith_search_field"
internal fun sectionTag(id: Int) = "hadith_section_$id"
