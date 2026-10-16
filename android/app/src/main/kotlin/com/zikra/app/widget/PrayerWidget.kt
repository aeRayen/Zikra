package com.zikra.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.os.UserManagerCompat
import com.zikra.app.MainActivity
import com.zikra.app.R
import com.zikra.app.ZikraApplication
import com.zikra.app.prayer.PrayerTimesSource
import com.zikra.app.prayer.PrayerTimesUiState
import com.zikra.app.ui.labelRes
import com.zikra.core.i18n.Bidi
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.TimeFormatter
import com.zikra.platform.alarm.AlarmHandler
import com.zikra.platform.alarm.AlarmKind
import com.zikra.platform.alarm.AlarmPlanner
import com.zikra.platform.alarm.AlarmScheduler
import com.zikra.platform.alarm.ReplanReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant

/** The three lines the widget shows; built from the same state as the prayer tab, so they always agree. */
data class WidgetText(val label: String, val title: String, val place: String)

/** Pure: prayer state -> widget text (tested without a widget host). */
fun widgetText(context: Context, state: PrayerTimesUiState): WidgetText? {
    val ready = state as? PrayerTimesUiState.Ready
    if (ready == null) {
        return if (state == PrayerTimesUiState.NoLocation) {
            WidgetText(context.getString(R.string.prayer_title), context.getString(R.string.widget_no_location), "")
        } else {
            null
        }
    }
    val locale = context.resources.configuration.locales[0]
    val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
    val time = TimeFormatter.format(ready.nextTime, locale, DateFormat.is24HourFormat(context), numerals)
    val template = if (ready.nextIsTomorrow) R.string.prayer_next_tomorrow else R.string.prayer_next_at
    val place = ready.place?.name ?: context.getString(R.string.location_approximate)
    return WidgetText(
        label = context.getString(R.string.prayer_next_label),
        title = context.getString(template, context.getString(ready.next.labelRes()), Bidi.isolate(time)),
        place = place,
    )
}

/**
 * Keeps the home-screen widgets right (WP 2.17): refreshes them on every system event (reboot, time
 * and time-zone changes - ADR-033), on settings changes, and at each prayer time via AlarmKind.WIDGET.
 */
class PrayerWidget(
    private val source: PrayerTimesSource,
    private val scheduler: (Context) -> AlarmScheduler = AlarmScheduler::get,
) : AlarmPlanner, AlarmHandler {

    suspend fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager?.getAppWidgetIds(ComponentName(context, PrayerWidgetProvider::class.java)) ?: IntArray(0)
        when {
            ids.isEmpty() -> scheduler(context).cancelAll(AlarmKind.WIDGET)
            // Before the first unlock settings cannot be read: the widget keeps its last text.
            !UserManagerCompat.isUserUnlocked(context) -> Unit
            else -> {
                val language = context.resources.configuration.locales[0].toLanguageTag()
                val state = source.states(flowOf(language)).first()
                widgetText(context, state)?.let { manager?.updateAppWidget(ids, views(context, it)) }
                nextChange(state)?.let { scheduler(context).replaceAll(AlarmKind.WIDGET, mapOf(KEY to it)) }
            }
        }
    }

    override suspend fun replan(context: Context, reason: ReplanReason) = refreshSafely(context)

    override suspend fun onAlarm(context: Context, key: String) = refreshSafely(context)

    private suspend fun refreshSafely(context: Context) {
        try {
            refresh(context)
        } catch (_: IOException) {
            Unit // settings unreadable for now: keep the last text, the next event refreshes again
        }
    }

    private fun views(context: Context, text: WidgetText) = RemoteViews(
        context.packageName,
        R.layout.widget_next_prayer
    ).apply {
        setTextViewText(R.id.widget_label, text.label)
        setTextViewText(R.id.widget_title, text.title)
        setTextViewText(R.id.widget_place, text.place)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        setOnClickPendingIntent(R.id.widget_root, open)
    }

    /** Just after the next prayer begins, the widget must show the one after it. */
    private fun nextChange(state: PrayerTimesUiState): Instant? {
        val ready = state as? PrayerTimesUiState.Ready ?: return null
        val day = if (ready.nextIsTomorrow) ready.date.plusDays(1) else ready.date
        return day.atTime(ready.nextTime).atZone(ready.zone).toInstant().plusSeconds(REFRESH_DELAY_S)
    }

    private companion object {
        const val KEY = "widget|next"
        const val REFRESH_DELAY_S = 30L
    }
}

/** Platform entry point; all work is in [PrayerWidget]. */
class PrayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val widget = (context.applicationContext as? ZikraApplication)?.container?.prayerWidget ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                widget.replan(context, ReplanReason.SETTINGS_CHANGED)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDisabled(context: Context) {
        AlarmScheduler.get(context).cancelAll(AlarmKind.WIDGET)
    }
}
