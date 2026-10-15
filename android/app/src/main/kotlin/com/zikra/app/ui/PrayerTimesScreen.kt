package com.zikra.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.TimeFormatter
import com.zikra.core.prayer.AsrMadhab
import com.zikra.core.prayer.CalculationMethodId
import com.zikra.core.prayer.PrayerName
import com.zikra.core.settings.LocationSource
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Today's prayer times (WP 2.3): next prayer with countdown, the six times, and the method in use. */
@Composable
fun PrayerTimesScreen(
    state: PrayerTimesUiState,
    onChooseLocation: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onOpenMonth: () -> Unit = {},
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ZikraSpacing.screenGutter)) {
        Text(
            text = stringResource(R.string.prayer_title),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() },
        )
        when (state) {
            PrayerTimesUiState.Loading -> Unit
            PrayerTimesUiState.NoLocation -> {
                EmptyState(
                    title = stringResource(R.string.prayer_no_location_title),
                    message = stringResource(R.string.prayer_no_location_message),
                )
                TextButton(onClick = onChooseLocation, modifier = Modifier.testTag(CHOOSE_LOCATION_TAG)) {
                    Text(text = stringResource(R.string.prayer_choose_location))
                }
            }
            is PrayerTimesUiState.Ready -> {
                ReadyContent(state, onChooseLocation)
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onOpenMonth, modifier = Modifier.testTag(OPEN_MONTH_TAG)) {
                        Text(text = stringResource(R.string.prayer_open_month))
                    }
                    OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag(OPEN_SETTINGS_TAG)) {
                        Text(text = stringResource(R.string.prayer_open_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyContent(state: PrayerTimesUiState.Ready, onChangeLocation: () -> Unit) {
    val formatTime = rememberTimeFormatter()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = locationTitle(state),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f).testTag(PRAYER_LOCATION_TAG),
        )
        TextButton(onClick = onChangeLocation) { Text(text = stringResource(R.string.prayer_change_location)) }
    }
    if (state.zoneDiffersFromDevice) {
        val zoneName = state.zone.getDisplayName(TextStyle.FULL, LocalConfiguration.current.locales[0])
        Text(
            text = stringResource(R.string.prayer_zone_note, Bidi.isolate(zoneName)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    NextPrayerCard(state, formatTime)
    state.times.forEach { (prayer, time) ->
        val isNext = prayer == state.next && !state.nextIsTomorrow
        PrayerRow(prayer, formatTime(time), isNext)
        HorizontalDivider()
    }
    Column(Modifier.padding(top = ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.prayer_method_used, stringResource(state.method.labelRes())),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.testTag(PRAYER_METHOD_TAG),
        )
        val asr = if (state.madhab == AsrMadhab.HANAFI) R.string.prayer_asr_hanafi else R.string.prayer_asr_standard
        Text(text = stringResource(asr), style = MaterialTheme.typography.bodyMedium)
        if (state.methodIsAutomatic) {
            Text(
                text = stringResource(R.string.prayer_method_automatic),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NextPrayerCard(state: PrayerTimesUiState.Ready, formatTime: (LocalTime) -> String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZikraSpacing.screenGutter)
            .semantics(mergeDescendants = true) {} // read as one sentence by TalkBack
            .testTag(NEXT_PRAYER_TAG),
    ) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = stringResource(R.string.prayer_next_label), style = MaterialTheme.typography.labelLarge)
            val template = if (state.nextIsTomorrow) R.string.prayer_next_tomorrow else R.string.prayer_next_at
            Text(
                text = stringResource(
                    template,
                    stringResource(state.next.labelRes()),
                    Bidi.isolate(formatTime(state.nextTime))
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(text = stringResource(R.string.prayer_remaining, remaining(state.minutesUntilNext)))
        }
    }
}

@Composable
private fun PrayerRow(prayer: PrayerName, time: String, isNext: Boolean) {
    val weight = if (isNext) FontWeight.Bold else FontWeight.Normal
    val colors = MaterialTheme.colorScheme
    val color = if (prayer == PrayerName.SUNRISE) colors.onSurfaceVariant else colors.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(prayerRowTag(prayer)),
    ) {
        Text(
            text = stringResource(prayer.labelRes()),
            fontWeight = weight,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Text(text = time, fontWeight = weight, color = color)
    }
}

@Composable
private fun locationTitle(state: PrayerTimesUiState.Ready): String {
    val place = state.place
    return when {
        place == null -> stringResource(R.string.location_approximate)
        state.source == LocationSource.DEVICE -> stringResource(R.string.location_near_city, Bidi.isolate(place.name))
        else -> place.name
    }
}

/** "1 hour and 20 minutes"; hours or minutes alone when the other is zero. Digits follow the language. */
@Composable
private fun remaining(totalMinutes: Long): String {
    val numerals = NumeralSystem.forLanguage(LocalConfiguration.current.locales[0].toLanguageTag())
    val hours = (totalMinutes / MINUTES_PER_HOUR).toInt()
    val minutes = (totalMinutes % MINUTES_PER_HOUR).toInt()
    val hoursText = pluralStringResource(R.plurals.prayer_hours, hours, numerals.apply(hours.toString()))
    val minutesText = pluralStringResource(R.plurals.prayer_minutes, minutes, numerals.apply(minutes.toString()))
    return when {
        hours == 0 -> minutesText
        minutes == 0 -> hoursText
        else -> stringResource(R.string.prayer_hours_and_minutes, hoursText, minutesText)
    }
}

@Composable
private fun rememberTimeFormatter(): (LocalTime) -> String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val use24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
    return { time -> TimeFormatter.format(time, locale, use24Hour, numerals) }
}

internal fun PrayerName.labelRes(): Int = when (this) {
    PrayerName.FAJR -> R.string.prayer_fajr
    PrayerName.SUNRISE -> R.string.prayer_sunrise
    PrayerName.DHUHR -> R.string.prayer_dhuhr
    PrayerName.ASR -> R.string.prayer_asr
    PrayerName.MAGHRIB -> R.string.prayer_maghrib
    PrayerName.ISHA -> R.string.prayer_isha
}

internal fun CalculationMethodId.labelRes(): Int = when (this) {
    CalculationMethodId.MUSLIM_WORLD_LEAGUE -> R.string.method_mwl
    CalculationMethodId.EGYPTIAN -> R.string.method_egyptian
    CalculationMethodId.KARACHI -> R.string.method_karachi
    CalculationMethodId.UMM_AL_QURA -> R.string.method_umm_al_qura
    CalculationMethodId.DUBAI -> R.string.method_dubai
    CalculationMethodId.MOONSIGHTING_COMMITTEE -> R.string.method_moonsighting
    CalculationMethodId.NORTH_AMERICA -> R.string.method_isna
    CalculationMethodId.KUWAIT -> R.string.method_kuwait
    CalculationMethodId.QATAR -> R.string.method_qatar
    CalculationMethodId.SINGAPORE -> R.string.method_singapore
    CalculationMethodId.TURKEY -> R.string.method_turkey
}

private const val MINUTES_PER_HOUR = 60L
internal const val CHOOSE_LOCATION_TAG = "prayer_choose_location"
internal const val PRAYER_LOCATION_TAG = "prayer_location"
internal const val PRAYER_METHOD_TAG = "prayer_method"
internal const val NEXT_PRAYER_TAG = "prayer_next"
internal const val OPEN_MONTH_TAG = "prayer_open_month"
internal const val OPEN_SETTINGS_TAG = "prayer_open_settings"
internal fun prayerRowTag(prayer: PrayerName) = "prayer_row_${prayer.name}"
