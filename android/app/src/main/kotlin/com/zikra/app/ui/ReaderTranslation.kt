package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
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
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ZikraSpacing.screenGutter)
                .padding(bottom = 12.dp)
                .semantics(mergeDescendants = true) {} // translation and its note are read together
                .testTag(translationTag(translated.ayah)),
        ) {
            Text(
                text = translated.text,
                fontSize = (TRANSLATION_SIZE_SP * textScale).sp,
                lineHeight = (TRANSLATION_SIZE_SP * textScale * TRANSLATION_LINE_HEIGHT).sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            translated.footnotes?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private const val TRANSLATION_SIZE_SP = 16f
private const val TRANSLATION_LINE_HEIGHT = 1.5f
