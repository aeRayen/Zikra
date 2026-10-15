package com.zikra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.adhan.AlertMode
import com.zikra.app.prayer.PrayerSettingsUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.prayer.PrayerName

/** «Alerts» section of the prayer settings (WP 2.5): off / silent / sound per prayer. */
internal fun LazyListScope.alertSection(state: PrayerSettingsUiState, onAlert: (PrayerName, AlertMode) -> Unit) {
    item { SettingsSectionHeader(R.string.settings_alerts_header, hint = R.string.settings_alerts_hint) }
    items(PrayerName.entries) { prayer -> AlertRow(prayer, state.alerts[prayer] ?: AlertMode.default(prayer), onAlert) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertRow(prayer: PrayerName, selected: AlertMode, onAlert: (PrayerName, AlertMode) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = ZikraSpacing.screenGutter, vertical = 6.dp)) {
        Text(text = stringResource(prayer.labelRes()), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            AlertMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = mode == selected,
                    onClick = { onAlert(prayer, mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, AlertMode.entries.size),
                    modifier = Modifier.testTag(alertTag(prayer, mode)),
                ) { Text(text = stringResource(mode.labelRes())) }
            }
        }
    }
}

private fun AlertMode.labelRes(): Int = when (this) {
    AlertMode.OFF -> R.string.alert_mode_off
    AlertMode.SILENT -> R.string.alert_mode_silent
    AlertMode.SOUND -> R.string.alert_mode_sound
}
