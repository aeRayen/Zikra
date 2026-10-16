package com.zikra.app.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.app.quran.QuranSearchUiState
import com.zikra.app.quran.SearchHit
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem

/** Quran search (WP 2.11). Results show the verbatim Uthmani ayah; tap opens it in the reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranSearchScreen(
    state: QuranSearchUiState,
    onQueryChange: (String) -> Unit,
    onOpen: (surah: Int, ayah: Int) -> Unit,
    onBack: () -> Unit,
) {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].language)
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.quran_search)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            label = { Text(text = stringResource(R.string.quran_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(
                horizontal = ZikraSpacing.screenGutter
            ).testTag(QURAN_SEARCH_FIELD_TAG),
        )
        val results = state.results
        when {
            state.unavailable -> Status(stringResource(R.string.quran_unavailable))
            results == null -> Unit
            results.isEmpty() -> Status(stringResource(R.string.quran_search_none))
            else -> {
                val count =
                    pluralStringResource(
                        R.plurals.quran_search_count,
                        state.total,
                        numerals.apply(state.total.toString())
                    )
                Status(count, tag = QURAN_SEARCH_COUNT_TAG)
                if (state.total > results.size) {
                    Status(
                        stringResource(
                            R.string.quran_search_more,
                            numerals.apply(results.size.toString()),
                            numerals.apply(state.total.toString()),
                        ),
                    )
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(results, key = { it.ayah.surah * AYAH_KEY + it.ayah.ayah }) { hit -> Result(hit, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun Status(text: String, tag: String? = null) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

@Composable
private fun Result(hit: SearchHit, onOpen: (Int, Int) -> Unit) {
    val (surah, ayah) = hit.surah to hit.ayah
    val language = LocalConfiguration.current.locales[0].language
    val numerals = NumeralSystem.forLanguage(language)
    val name = if (language == "ar") surah.nameArabic else surah.nameTransliterated
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onOpen(ayah.surah, ayah.ayah) }
            .padding(horizontal = ZikraSpacing.screenGutter, vertical = 10.dp)
            .testTag(searchResultTag(ayah.surah, ayah.ayah)),
    ) {
        Text(
            text = stringResource(
                R.string.quran_search_result_ref,
                Bidi.isolate(name),
                numerals.apply(ayah.ayah.toString())
            ),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Text(
                text = ayah.text,
                fontFamily = QuranFont,
                fontSize = 20.sp,
                lineHeight = 36.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    HorizontalDivider()
}

private const val AYAH_KEY = 1000
internal const val QURAN_SEARCH_FIELD_TAG = "quran_search_field"
internal const val QURAN_SEARCH_COUNT_TAG = "quran_search_count"
internal fun searchResultTag(surah: Int, ayah: Int) = "quran_result_${surah}_$ayah"
