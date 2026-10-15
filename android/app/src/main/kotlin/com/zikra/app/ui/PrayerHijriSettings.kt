package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.prayer.PrayerSettingsUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.HijriCalendar
import com.zikra.core.i18n.NumeralSystem

/** «Hijri date» section (WP 2.7): ±2 days with a live preview of today's date. */
internal fun LazyListScope.hijriSection(state: PrayerSettingsUiState, onHijri: (Int) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_hijri_header, hint = R.string.settings_hijri_hint) }
    item {
        val locale = LocalConfiguration.current.locales[0]
        val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
        val days = state.hijriAdjustment
        Row(
            Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val preview = state.hijriToday?.let { HijriCalendar.format(it, locale, numerals) } ?: ""
            Text(
                text = stringResource(R.string.settings_hijri_preview, Bidi.isolate(preview)),
                modifier = Modifier.weight(1f).testTag(HIJRI_PREVIEW_TAG),
            )
            StepButton("−", stringResource(R.string.settings_hijri_minus), hijriTag(-1)) { onHijri(-1) }
            Text(
                text = Bidi.isolate(numerals.apply(if (days > 0) "+$days" else days.toString())),
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(min = 32.dp),
            )
            StepButton("+", stringResource(R.string.settings_hijri_plus), hijriTag(1)) { onHijri(1) }
        }
    }
}
