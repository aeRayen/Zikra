package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zikra.app.R
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.quran.TranslatedAyah

/** The translation is shown in its own direction (LTR for English), under the Arabic, never instead of it. */
@Composable
internal fun TranslationText(translated: TranslatedAyah, textScale: Float) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter).padding(bottom = 12.dp)) {
            Text(
                text = translated.text,
                fontSize = (TRANSLATION_SIZE_SP * textScale).sp,
                lineHeight = (TRANSLATION_SIZE_SP * textScale * TRANSLATION_LINE_HEIGHT).sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag(translationTag(translated.ayah)),
            )
            // The publisher's notes are kept verbatim but folded: some are long and would bury the ayahs.
            translated.footnotes?.let { notes ->
                var open by rememberSaveable(translated.surah, translated.ayah) { mutableStateOf(false) }
                TextButton(onClick = { open = !open }, modifier = Modifier.testTag(notesTag(translated.ayah))) {
                    Text(text = stringResource(if (open) R.string.reader_hide_notes else R.string.reader_show_notes))
                }
                if (open) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
internal fun TranslationCredit(title: String) {
    Text(
        text = stringResource(R.string.reader_translation_credit, title),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(8.dp),
    )
}

internal fun notesTag(ayah: Int) = "quran_notes_$ayah"

private const val TRANSLATION_SIZE_SP = 16f
private const val TRANSLATION_LINE_HEIGHT = 1.5f
