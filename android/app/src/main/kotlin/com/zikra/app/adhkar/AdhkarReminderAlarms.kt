package com.zikra.app.adhkar

import android.content.Context
import androidx.core.os.UserManagerCompat
import com.zikra.app.prayer.PrayerContext
import com.zikra.core.places.PlaceLookup
import com.zikra.core.settings.AdhkarRemindersStore
import com.zikra.core.settings.LocationStore
import com.zikra.core.settings.PrayerPreferencesStore
import com.zikra.platform.alarm.AlarmHandler
import com.zikra.platform.alarm.AlarmKind
import com.zikra.platform.alarm.AlarmPlanner
import com.zikra.platform.alarm.AlarmScheduler
import com.zikra.platform.alarm.ReplanReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Clock

/**
 * Keeps the adhkar reminders scheduled (WP 2.14c), the same way as prayer alerts (ADR-033/034): plans
 * [AdhkarReminderPlan.DAYS_AHEAD] days, re-plans on every change and system event, and rolls forward
 * each time a reminder fires.
 */
class AdhkarReminderAlarms(
    private val places: () -> PlaceLookup,
    private val location: LocationStore,
    private val prayer: PrayerPreferencesStore,
    private val reminders: AdhkarRemindersStore,
    private val scheduler: (Context) -> AlarmScheduler = AlarmScheduler::get,
    private val clock: () -> Clock = Clock::systemDefaultZone,
) : AlarmPlanner, AlarmHandler {

    /** Plans from the current settings. Credential storage must be unlocked (callers check). */
    override suspend fun replan(context: Context, reason: ReplanReason) {
        val choice = reminders.data.first()
        if (!choice.anyOn) {
            scheduler(context).cancelAll(AlarmKind.ADHKAR)
            return
        }
        val phone = clock()
        val place = location.location.first()?.let { saved ->
            val ctx = PrayerContext.of(saved, prayer.preferences.first(), places(), "en", phone.zone)
            PrayerPlace(ctx.point, ctx.zone, ctx.resolved.settings)
        }
        scheduler(context).replaceAll(
            AlarmKind.ADHKAR,
            AdhkarReminderPlan.reminders(choice, place, phone.zone, phone.instant()),
        )
    }

    override suspend fun onAlarm(context: Context, key: String) {
        val reminder = AdhkarReminderPlan.parse(key) ?: return
        AdhkarReminderNotifier.post(context, reminder, clock().instant())
        if (UserManagerCompat.isUserUnlocked(context)) {
            try {
                replan(context, ReplanReason.SETTINGS_CHANGED)
            } catch (_: IOException) {
                Unit // the remaining planned days still fire; the next event plans again
            }
        }
    }

    /** Re-plans whenever the reminders, the location or the prayer settings change. */
    fun observe(context: Context, scope: CoroutineScope) {
        scope.launch {
            combine(reminders.data, location.location, prayer.preferences) { r, l, p -> Triple(r, l, p) }
                .distinctUntilChanged()
                .collect {
                    try {
                        replan(context, ReplanReason.SETTINGS_CHANGED)
                    } catch (_: IOException) {
                        Unit
                    }
                }
        }
    }
}
