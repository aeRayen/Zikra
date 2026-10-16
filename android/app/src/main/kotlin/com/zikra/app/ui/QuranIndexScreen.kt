package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.app.quran.JuzStart
import com.zikra.app.quran.QuranIndexUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.quran.Surah

/** Mushaf font (Amiri Quran, SIL OFL 1.1): covers every code point of the Tanzil text (tested). */
val QuranFont = FontFamily(Font(com.zikra.core.quran.R.font.amiri_quran))

/** Book icon for the Quran tab. Path from Material Icons «menu_book» (Apache License 2.0). */
val QuranTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "QuranTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .addPath(
            pathData = addPathNodes(
                "M21,5c-1.11,-0.35 -2.33,-0.5 -3.5,-0.5c-1.95,0 -4.05,0.4 -5.5,1.5c-1.45,-1.1 -3.55,-1.5 -5.5,-1.5" +
                    "S2.45,4.9 1,6v14.65c0,0.25 0.25,0.5 0.5,0.5c0.1,0 0.15,-0.05 0.25,-0.05C3.1,20.45 5.05,20 6.5,20" +
                    "c1.95,0 4.05,0.4 5.5,1.5c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.65,0 3.35,0.3 4.75,1.05" +
                    "c0.1,0.05 0.15,0.05 0.25,0.05" +
                    "c0.25,0 0.5,-0.25 0.5,-0.5V6C22.4,5.55 21.75,5.25 21,5zM21,18.5c-1.1,-0.35 -2.3,-0.5 -3.5,-0.5" +
                    "c-1.7,0 -4.15,0.65 -5.5,1.5V8c1.35,-0.85 3.8,-1.5 5.5,-1.5c1.2,0 2.4,0.15 3.5,0.5V18.5z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}

/** Surah and juz index (WP 2.10). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuranIndexScreen(
    state: QuranIndexUiState,
    onOpen: (surah: Int, ayah: Int) -> Unit,
    onAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.quran_title)) },
            actions = {
                IconButton(onClick = onAbout) {
                    Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.about_text_title))
                }
            },
        )
        when (state) {
            QuranIndexUiState.Loading -> Unit
            QuranIndexUiState.Unavailable -> EmptyState(
                title = stringResource(R.string.quran_title),
                message = stringResource(R.string.quran_unavailable),
            )
            is QuranIndexUiState.Ready -> {
                TabRow(selectedTabIndex = tab) {
                    Tab(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        text = { Text(text = stringResource(R.string.quran_tab_surahs)) }
                    )
                    Tab(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        text = { Text(text = stringResource(R.string.quran_tab_juz)) }
                    )
                }
                if (tab == 0) SurahList(state.surahs, onOpen) else JuzList(state.juz, onOpen)
            }
        }
    }
}

@Composable
private fun SurahList(surahs: List<Surah>, onOpen: (Int, Int) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val numerals = NumeralSystem.forLanguage(language)
    LazyColumn(Modifier.fillMaxSize().testTag(SURAH_LIST_TAG)) {
        items(surahs, key = { it.number }) { surah ->
            val place = if (surah.revelationPlace.startsWith("Mec")) R.string.quran_meccan else R.string.quran_medinan
            val count = pluralStringResource(
                R.plurals.quran_ayahs,
                surah.ayahCount,
                numerals.apply(surah.ayahCount.toString())
            )
            ListItem(
                leadingContent = {
                    Text(
                        text = numerals.apply(surah.number.toString()),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                headlineContent = {
                    // Arabic UI: the Arabic name; other languages: the transliteration (plus the English meaning).
                    Text(text = if (language == "ar") surah.nameArabic else surah.nameTransliterated)
                },
                supportingContent = {
                    val meaning = if (language == "ar") "" else Bidi.isolate(surah.nameEnglish) + " · "
                    Text(text = meaning + stringResource(place) + " · " + count)
                },
                trailingContent = {
                    if (language != "ar") Text(text = surah.nameArabic, fontFamily = QuranFont, fontSize = 20.sp)
                },
                modifier = Modifier.clickable(
                    role = Role.Button
                ) { onOpen(surah.number, 1) }.testTag(surahRowTag(surah.number)),
            )
        }
    }
}

@Composable
private fun JuzList(juz: List<JuzStart>, onOpen: (Int, Int) -> Unit) {
    val language = LocalConfiguration.current.locales[0].language
    val numerals = NumeralSystem.forLanguage(language)
    LazyColumn(Modifier.fillMaxSize()) {
        items(juz, key = { it.juz }) { start ->
            val surahName = if (language == "ar") start.surah.nameArabic else start.surah.nameTransliterated
            ListItem(
                headlineContent = {
                    Text(
                        text = stringResource(R.string.quran_juz_label, numerals.apply(start.juz.toString()))
                    )
                },
                supportingContent = {
                    Text(
                        text = stringResource(
                            R.string.quran_juz_starts,
                            Bidi.isolate(surahName),
                            numerals.apply(start.ayah.toString())
                        )
                    )
                },
                modifier = Modifier
                    .clickable(role = Role.Button) { onOpen(start.surah.number, start.ayah) }
                    .padding(vertical = 2.dp)
                    .testTag(juzRowTag(start.juz)),
            )
        }
    }
}

internal const val SURAH_LIST_TAG = "quran_surah_list"
internal fun surahRowTag(number: Int) = "quran_surah_$number"
internal fun juzRowTag(number: Int) = "quran_juz_$number"
