package com.zikra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.prayer.MonthDay
import com.zikra.app.prayer.PrayerMonthUiState
import com.zikra.core.designsystem.component.EmptyState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.TimeFormatter
import com.zikra.core.prayer.PrayerName
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

/** Monthly timetable (WP 2.3). Wide table: scrolls sideways at large font sizes instead of truncating. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrayerMonthScreen(
    state: PrayerMonthUiState,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.prayer_open_month)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        when (state) {
            PrayerMonthUiState.Loading -> Unit
            PrayerMonthUiState.NoLocation -> EmptyState(
                title = stringResource(R.string.prayer_no_location_title),
                message = stringResource(R.string.prayer_no_location_message),
            )
            is PrayerMonthUiState.Ready -> {
                MonthHeader(state, onPrevious, onNext)
                MonthTable(state)
            }
        }
    }
}

@Composable
private fun MonthHeader(state: PrayerMonthUiState.Ready, onPrevious: () -> Unit, onNext: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
    val title = numerals.apply(DateTimeFormatter.ofPattern("LLLL yyyy", locale).format(state.month))
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        // AutoMirrored: «previous» points to the start edge (right in Arabic).
        IconButton(onClick = onPrevious, modifier = Modifier.testTag(MONTH_PREVIOUS_TAG)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.month_previous)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() }.testTag(MONTH_TITLE_TAG),
        )
        IconButton(onClick = onNext, modifier = Modifier.testTag(MONTH_NEXT_TAG)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.month_next))
        }
    }
}

@Composable
private fun MonthTable(state: PrayerMonthUiState.Ready) {
    val fontScale = LocalDensity.current.fontScale
    val dayWidth = DAY_COLUMN * fontScale
    val timeWidth = TIME_COLUMN * fontScale
    val tableWidth = dayWidth + timeWidth * PrayerName.entries.size + ZikraSpacing.screenGutter * 2
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        LazyColumn(Modifier.width(tableWidth).padding(horizontal = ZikraSpacing.screenGutter)) {
            item {
                Row(Modifier.padding(vertical = 8.dp)) {
                    HeaderCell(stringResource(R.string.month_day_column), dayWidth)
                    PrayerName.entries.forEach { HeaderCell(stringResource(it.labelRes()), timeWidth) }
                }
                HorizontalDivider()
            }
            items(
                state.days,
                key = { it.date.toEpochDay() }
            ) { day -> DayRow(day, day.date == state.today, dayWidth, timeWidth) }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width),
    )
}

@Composable
private fun DayRow(day: MonthDay, isToday: Boolean, dayWidth: Dp, timeWidth: Dp) {
    val locale = LocalConfiguration.current.locales[0]
    val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
    val use24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val weekday = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
    val background = if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
    Row(
        Modifier
            .background(background)
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) {}
            .testTag(monthRowTag(day.date.dayOfMonth)),
    ) {
        Text(
            text = numerals.apply("${day.date.dayOfMonth}") + " " + weekday,
            fontWeight = weight,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(dayWidth),
        )
        day.times.forEach { time ->
            Text(
                text = TimeFormatter.formatCompact(time, use24Hour, numerals),
                fontWeight = weight,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(timeWidth),
            )
        }
    }
}

private val DAY_COLUMN = 64.dp
private val TIME_COLUMN = 52.dp
internal const val MONTH_TITLE_TAG = "month_title"
internal const val MONTH_PREVIOUS_TAG = "month_previous"
internal const val MONTH_NEXT_TAG = "month_next"
internal fun monthRowTag(day: Int) = "month_row_$day"
