package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.prayer.PrayerSettingsUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.HighLatitudeRule
import com.zikra.core.prayer.PrayerName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerSettingsScreen(
    state: PrayerSettingsUiState,
    actions: PrayerSettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.prayer_settings_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        if (!state.loaded) return@Column
        if (state.saveFailed) {
            Text(
                text = stringResource(R.string.error_save_failed),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(horizontal = ZikraSpacing.screenGutter)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            alertSection(state, actions.onAlert)
            methodSection(state, actions.onMethod)
            madhabSection(state, actions.onMadhab)
            highLatitudeSection(state, actions.onHighLatitudeRule)
            adjustmentSection(state, actions.onAdjust)
            hijriSection(state, actions.onHijri)
            item {
                OutlinedButton(
                    onClick = actions.onReset,
                    modifier = Modifier.padding(ZikraSpacing.screenGutter).testTag(RESET_TAG),
                ) { Text(text = stringResource(R.string.settings_reset)) }
            }
        }
    }
}

private fun LazyListScope.methodSection(state: PrayerSettingsUiState, onMethod: (CalculationMethodId?) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_method_header) }
    item {
        val auto = stringResource(R.string.settings_automatic_format, stringResource(state.automaticMethod.labelRes()))
        Choice(auto, state.method == null, methodTag(null)) { onMethod(null) }
    }
    items(CalculationMethodId.entries) { method ->
        Choice(stringResource(method.labelRes()), state.method == method, methodTag(method)) { onMethod(method) }
    }
}

private fun LazyListScope.madhabSection(state: PrayerSettingsUiState, onMadhab: (AsrMadhab?) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_asr_header) }
    item {
        val auto =
            stringResource(R.string.settings_automatic_format, stringResource(state.automaticMadhab.settingsLabelRes()))
        Choice(auto, state.madhab == null, madhabTag(null)) { onMadhab(null) }
    }
    items(AsrMadhab.entries) { madhab ->
        Choice(
            stringResource(madhab.settingsLabelRes()),
            state.madhab == madhab,
            madhabTag(madhab)
        ) { onMadhab(madhab) }
    }
}

private fun LazyListScope.highLatitudeSection(state: PrayerSettingsUiState, onRule: (HighLatitudeRule?) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_high_lat_header, hint = R.string.settings_high_lat_hint) }
    item {
        Choice(
            stringResource(R.string.settings_high_lat_auto),
            state.highLatitudeRule == null,
            null
        ) { onRule(null) }
    }
    items(HighLatitudeRule.entries) { rule ->
        val label = when (rule) {
            HighLatitudeRule.MIDDLE_OF_THE_NIGHT -> R.string.settings_high_lat_middle
            HighLatitudeRule.SEVENTH_OF_THE_NIGHT -> R.string.settings_high_lat_seventh
            HighLatitudeRule.TWILIGHT_ANGLE -> R.string.settings_high_lat_angle
        }
        Choice(stringResource(label), state.highLatitudeRule == rule, null) { onRule(rule) }
    }
}

private fun LazyListScope.adjustmentSection(state: PrayerSettingsUiState, onAdjust: (PrayerName, Int) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_adjust_header, hint = R.string.settings_adjust_hint) }
    items(PrayerName.entries) { prayer -> AdjustmentRow(prayer, state.adjustments[prayer] ?: 0, onAdjust) }
}

@Composable
internal fun SettingsSectionHeader(title: Int, hint: Int? = null) {
    Column(
        Modifier.padding(start = ZikraSpacing.screenGutter, end = ZikraSpacing.screenGutter, top = 24.dp, bottom = 8.dp)
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, tag: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = label) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .let { if (tag != null) it.testTag(tag) else it },
    )
}

@Composable
private fun AdjustmentRow(prayer: PrayerName, minutes: Int, onAdjust: (PrayerName, Int) -> Unit) {
    val name = stringResource(prayer.labelRes())
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].toLanguageTag())
    // Signed value isolated as left-to-right so "+3" never renders as "3+" in Arabic.
    val value = Bidi.isolate(numerals.apply(if (minutes > 0) "+$minutes" else minutes.toString()))
    Row(
        Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = name, modifier = Modifier.weight(1f))
        StepButton(
            "−",
            stringResource(R.string.settings_adjust_minus, name),
            adjustTag(prayer, -1)
        ) { onAdjust(prayer, -1) }
        Text(
            text = value,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 40.dp).testTag(adjustValueTag(prayer)),
        )
        StepButton(
            "+",
            stringResource(R.string.settings_adjust_plus, name),
            adjustTag(prayer, 1)
        ) { onAdjust(prayer, 1) }
    }
}

/** The glyph is visual only; TalkBack reads [spoken] (e.g. «One minute later for Fajr»). */
@Composable
internal fun StepButton(glyph: String, spoken: String, tag: String, onClick: () -> Unit) {
    OutlinedIconButton(
        onClick = onClick,
        modifier = Modifier.testTag(tag).semantics { contentDescription = spoken },
    ) {
        Text(text = glyph, textAlign = TextAlign.Center, modifier = Modifier.clearAndSetSemantics {})
    }
}

private fun AsrMadhab.settingsLabelRes(): Int = when (this) {
    AsrMadhab.STANDARD -> R.string.settings_asr_standard
    AsrMadhab.HANAFI -> R.string.settings_asr_hanafi
}
