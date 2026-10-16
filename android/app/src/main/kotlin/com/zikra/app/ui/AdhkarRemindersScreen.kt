package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.zikra.app.R
import com.zikra.app.adhkar.AdhkarRemindersUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import java.time.LocalTime

/** Adhkar reminders (WP 2.14c): morning after Fajr, evening after Asr, sleep at a chosen time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhkarRemindersScreen(
    state: AdhkarRemindersUiState,
    actions: AdhkarRemindersActions,
    modifier: Modifier = Modifier,
    alertsNotice: AlertsNotice = AlertsNotice.NONE,
    saveFailed: Boolean = false,
) {
    var pickingTime by rememberSaveable { mutableStateOf(false) }
    val sleepTime = state.reminders.sleepMinutes.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.adhkar_reminders_title)) },
            navigationIcon = {
                IconButton(onClick = actions.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (state.reminders.anyOn) {
                Column(Modifier.padding(horizontal = ZikraSpacing.screenGutter)) {
                    AlertsNoticeCard(alertsNotice, actions.onFixAlerts)
                }
            }
            if (saveFailed) {
                Text(
                    text = stringResource(R.string.error_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = ZikraSpacing.screenGutter),
                )
            }
            ReminderRows(state, actions, sleepTime, onPickTime = { pickingTime = true })
        }
    }
    if (pickingTime) {
        SleepTimeDialog(
            initial = sleepTime,
            onDismiss = { pickingTime = false },
            onPick = { h, m ->
                pickingTime = false
                actions.onSleepTime(h, m)
            },
        )
    }
}

@Composable
private fun ReminderRows(
    state: AdhkarRemindersUiState,
    actions: AdhkarRemindersActions,
    sleepTime: LocalTime,
    onPickTime: () -> Unit,
) {
    val r = state.reminders
    ReminderRow(
        title = stringResource(R.string.adhkar_reminder_morning),
        detail = stringResource(R.string.adhkar_reminder_morning_when),
        on = r.morning,
        onChange = actions.onMorning,
        tag = REMINDER_MORNING_TAG,
    )
    ReminderRow(
        title = stringResource(R.string.adhkar_reminder_evening),
        detail = stringResource(R.string.adhkar_reminder_evening_when),
        on = r.evening,
        onChange = actions.onEvening,
        tag = REMINDER_EVENING_TAG,
    )
    val prayerBased = r.morning || r.evening
    if (prayerBased && state.loaded && !state.hasLocation) NeedsLocation(actions.onChooseLocation)
    ReminderRow(
        title = stringResource(R.string.adhkar_reminder_sleep),
        detail = stringResource(R.string.adhkar_reminder_sleep_when, rememberTimeFormatter()(sleepTime)),
        on = r.sleep,
        onChange = actions.onSleep,
        tag = REMINDER_SLEEP_TAG,
        onClick = onPickTime,
    )
}

@Composable
private fun ReminderRow(
    title: String,
    detail: String,
    on: Boolean,
    onChange: (Boolean) -> Unit,
    tag: String,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = { Text(text = detail) },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange, modifier = Modifier.testTag(tag)) },
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick).testTag(tag + ROW_SUFFIX) else Modifier,
    )
}

@Composable
private fun NeedsLocation(onChooseLocation: () -> Unit) {
    Column(Modifier.padding(horizontal = ZikraSpacing.screenGutter).testTag(REMINDER_NEEDS_LOCATION_TAG)) {
        Text(
            text = stringResource(R.string.adhkar_reminder_needs_location),
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onChooseLocation) { Text(text = stringResource(R.string.prayer_choose_location)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SleepTimeDialog(initial: LocalTime, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val picker = rememberTimePickerState(initial.hour, initial.minute, is24)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onPick(picker.hour, picker.minute) },
                modifier = Modifier.testTag(SLEEP_TIME_OK_TAG),
            ) { Text(text = stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(android.R.string.cancel)) }
        },
        text = { TimePicker(state = picker) },
    )
}

private const val MINUTES_PER_HOUR = 60
private const val ROW_SUFFIX = "_row"
internal const val REMINDER_MORNING_TAG = "reminder_morning"
internal const val REMINDER_EVENING_TAG = "reminder_evening"
internal const val REMINDER_SLEEP_TAG = "reminder_sleep"
internal const val REMINDER_NEEDS_LOCATION_TAG = "reminder_needs_location"
internal const val SLEEP_TIME_OK_TAG = "sleep_time_ok"
