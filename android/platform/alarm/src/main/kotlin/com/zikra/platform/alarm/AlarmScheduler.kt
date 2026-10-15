package com.zikra.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.AlarmManagerCompat
import androidx.core.net.toUri
import java.io.File
import java.time.Instant

/** How alarms are currently delivered; the UI explains [INEXACT] and offers the system setting. */
enum class DeliveryMode {
    /** On time, also in Doze. */
    EXACT,

    /** The user (or the system, Android 14+) has not allowed exact alarms: may arrive some minutes late. */
    INEXACT,
}

/**
 * The single alarm scheduler of the app (ADR-033). Every alarm goes through here, so reboot, time-zone
 * change and permission change can re-arm all of them consistently.
 */
class AlarmScheduler internal constructor(
    private val context: Context,
    private val registry: AlarmRegistry,
    private val now: () -> Long,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun deliveryMode(): DeliveryMode =
        if (AlarmManagerCompat.canScheduleExactAlarms(alarmManager)) DeliveryMode.EXACT else DeliveryMode.INEXACT

    /**
     * Makes [times] (key -> instant) the complete set of alarms of [kind]: new ones are armed, changed
     * ones re-armed, missing ones cancelled. Past times are ignored.
     */
    @Synchronized
    fun replaceAll(kind: AlarmKind, times: Map<String, Instant>) {
        val future = times.filterValues { it.toEpochMilli() > now() }.map { (key, at) -> key to at.toEpochMilli() }
        val change = registry.replace(kind, future)
        change.removed.forEach { cancel(it) }
        change.added.forEach { arm(it) }
    }

    @Synchronized
    fun cancelAll(kind: AlarmKind) = replaceAll(kind, emptyMap())

    /** Re-arms everything still in the future (after reboot, time change or permission change). */
    @Synchronized
    fun rearmAll() {
        registry.pruneBefore(now()).forEach { arm(it) }
    }

    @Synchronized
    internal fun onFired(requestCode: Int) = registry.remove(requestCode)

    fun scheduled(): List<AlarmEntry> = registry.entries()

    private fun arm(entry: AlarmEntry) {
        val operation = pendingIntent(entry, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        val at = entry.triggerAtEpochMs
        when {
            entry.kind == AlarmKind.USER_ALARM && deliveryMode() == DeliveryMode.EXACT ->
                AlarmManagerCompat.setAlarmClock(alarmManager, at, showAppIntent() ?: operation, operation)
            deliveryMode() == DeliveryMode.EXACT ->
                AlarmManagerCompat.setExactAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, at, operation)
            // Fallback (no exact permission): still fires in Doze, possibly some minutes late.
            else -> AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, at, operation)
        }
    }

    /** Opened when the user taps the system's «next alarm» indicator. */
    private fun showAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, SHOW_APP_REQUEST_CODE, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun cancel(entry: AlarmEntry) {
        pendingIntent(entry, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(entry: AlarmEntry, flag: Int): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_FIRE)
            // Unique data per request code: PendingIntents must not be merged with each other.
            .setData("zikra-alarm://${entry.requestCode}".toUri())
            .putExtra(EXTRA_KEY, entry.key)
            .putExtra(EXTRA_KIND, entry.kind.name)
            .putExtra(EXTRA_REQUEST_CODE, entry.requestCode)
        return PendingIntent.getBroadcast(context, entry.requestCode, intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        internal const val ACTION_FIRE = "com.zikra.platform.alarm.FIRE"
        internal const val EXTRA_KEY = "key"
        internal const val EXTRA_KIND = "kind"
        internal const val EXTRA_REQUEST_CODE = "request_code"
        private const val REGISTRY_FILE = "alarms/registry.json"
        private const val SHOW_APP_REQUEST_CODE = 1

        @Volatile
        private var instance: AlarmScheduler? = null

        /** Process-wide instance; its registry lives in device-protected storage (readable before unlock). */
        fun get(context: Context): AlarmScheduler = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                val deviceStorage = app.createDeviceProtectedStorageContext()
                val registry = AlarmRegistry(File(deviceStorage.filesDir, REGISTRY_FILE))
                AlarmScheduler(app, registry, System::currentTimeMillis)
                    .also { instance = it }
            }
        }

        /** Opens the system screen where the user allows exact alarms (Android 12+), or null if not needed. */
        fun exactAlarmSettingsIntent(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
            } else {
                null
            }
    }
}
