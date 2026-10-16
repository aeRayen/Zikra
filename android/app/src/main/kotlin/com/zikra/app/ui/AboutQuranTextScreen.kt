package com.zikra.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.zikra.app.R
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.quran.TranslationInfo

/**
 * «About this text» (WP 2.10, Tanzil licence): source, version, the licence notice as published
 * (left-to-right, it is English), the font and its licence, and a link to tanzil.net.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutQuranTextScreen(
    sourceVersion: String,
    licenseNotice: String,
    onBack: () -> Unit,
    translation: TranslationInfo? = null,
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.about_text_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZikraSpacing.screenGutter),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.about_text_source, Bidi.isolate(sourceVersion)),
                modifier = Modifier.testTag(ABOUT_SOURCE_TAG)
            )
            Text(text = stringResource(R.string.about_text_verbatim))
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, TANZIL_URL.toUri())) }) {
                Text(text = stringResource(R.string.about_text_open_tanzil))
            }
            Text(
                text = stringResource(R.string.about_text_license_header),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            // The notice is published in English: keep it left-to-right even in an Arabic UI.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = licenseNotice,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(ABOUT_NOTICE_TAG)
                )
            }
            Text(text = stringResource(R.string.about_text_font))
            translation?.let { TranslationSection(it) }
        }
    }
}

/** Each translation shows its source and terms (WP 2.13 acceptance). */
@Composable
private fun TranslationSection(info: TranslationInfo) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.about_translation_header),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() },
    )
    Text(
        text = stringResource(
            R.string.about_translation_body,
            Bidi.isolate(info.title),
            Bidi.isolate(info.version),
            Bidi.isolate(info.publisher),
            Bidi.isolate(info.license),
        ),
        modifier = Modifier.testTag(ABOUT_TRANSLATION_TAG),
    )
    Text(text = stringResource(R.string.about_translation_note), style = MaterialTheme.typography.bodySmall)
    if (info.sourceUrl.startsWith("https://")) {
        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, info.sourceUrl.toUri())) }) {
            Text(text = stringResource(R.string.about_translation_open))
        }
    }
}

private const val TANZIL_URL = "https://tanzil.net"
internal const val ABOUT_SOURCE_TAG = "about_text_source"
internal const val ABOUT_NOTICE_TAG = "about_text_notice"
internal const val ABOUT_TRANSLATION_TAG = "about_translation"
