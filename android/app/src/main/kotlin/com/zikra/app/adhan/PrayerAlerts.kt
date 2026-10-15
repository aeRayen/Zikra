package com.zikra.app.adhan

import android.content.Context
import androidx.core.os.UserManagerCompat
import com.zikra.app.prayer.PrayerContext
import com.zikra.core.places.PlaceLookup
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
 * Keeps the prayer alerts scheduled (WP 2.5): plans [AdhanPlan.DAYS_AHEAD] days of alerts from the
 * saved location and preferences, re-plans on every change and system event, and rolls the window
 * forward each time an alert fires - so alerts continue even if the app is never opened.
 */
class PrayerAlerts(
    private val places: () -> PlaceLookup,
    private val location: LocationStore,
    private val preferences: PrayerPreferencesStore,
    private val scheduler: (Context) -> AlarmScheduler = AlarmScheduler::get,
    private val clock: () -> Clock = Clock::systemDefaultZone,
) : AlarmPlanner, AlarmHandler {

    /** Plans from the current settings. Credential storage must be unlocked (callers check). */
    override suspend fun replan(context: Context, reason: ReplanReason) {
        val saved = location.location.first()
        if (saved == null) {
            scheduler(context).cancelAll(AlarmKind.ADHAN)
            return
        }
        val prefs = preferences.preferences.first()
        val phone = clock()
        val ctx = PrayerContext.of(saved, prefs, places(), "en", phone.zone)
        val alerts = AdhanPlan.alerts(ctx.point, ctx.zone, ctx.resolved.settings, prefs.alerts, phone.instant())
        scheduler(context).replaceAll(AlarmKind.ADHAN, alerts)
    }

    override suspend fun onAlarm(context: Context, key: String) {
        val alert = AdhanPlan.parse(key) ?: return
        val phone = clock()
        PrayerAlertNotifier.post(context, alert, phone.instant(), phone.zone)
        // Roll the window forward (needs settings: only once the phone has been unlocked since boot).
        if (UserManagerCompat.isUserUnlocked(context)) {
            try {
                replan(context, ReplanReason.SETTINGS_CHANGED)
            } catch (_: IOException) {
                Unit // the remaining planned days still fire; the next event plans again
            }
        }
    }

    /** Re-plans whenever the location or prayer preferences change (including the first value at start). */
    fun observe(context: Context, scope: CoroutineScope) {
        scope.launch {
            combine(location.location, preferences.preferences) { loc, prefs -> loc to prefs }
                .distinctUntilChanged()
                .collect {
                    // A failed read/write must not crash the app from a background scope; the next change or
                    // system event plans again.
                    try {
                        replan(context, ReplanReason.SETTINGS_CHANGED)
                    } catch (_: IOException) {
                        Unit
                    }
                }
        }
    }
}
