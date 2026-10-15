package com.zikra.app.adhan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikra.app.MainActivity
import com.zikra.app.R
import com.zikra.core.i18n.NumeralSystem
import com.zikra.core.i18n.TimeFormatter
import com.zikra.core.prayer.PrayerName
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Posts prayer alerts (WP 2.5). Uses only resources and the alarm key, never credential storage, so it
 * also works when the alarm fires before the first unlock after a reboot.
 */
object PrayerAlertNotifier {
    const val CHANNEL_SOUND = "prayer_alerts_sound"
    const val CHANNEL_SILENT = "prayer_alerts_silent"

    /** An alert delivered this late (phone was off, Doze) would mislead: «time for Dhuhr» at Asr. */
    val STALE_AFTER: Duration = Duration.ofMinutes(30)

    private const val NOTIFICATION_ID_BASE = 2_000

    /** (Re)creates the channels; names follow the current language, so call it on start and locale change. */
    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val sound = NotificationChannel(
            CHANNEL_SOUND,
            context.getString(R.string.channel_prayer_sound),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.channel_prayer_sound_description) }
        val silent = NotificationChannel(
            CHANNEL_SILENT,
            context.getString(R.string.channel_prayer_silent),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_prayer_silent_description)
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(listOf(sound, silent))
    }

    /** Returns false when nothing was posted (stale, off, or notifications not allowed). */
    fun post(context: Context, alert: AdhanPlan.Alert, now: Instant, zone: ZoneId): Boolean {
        val stale = Duration.between(alert.at, now) > STALE_AFTER
        val notifications = NotificationManagerCompat.from(context)
        if (alert.mode == AlertMode.OFF || stale || !notifications.areNotificationsEnabled()) return false
        ensureChannels(context)
        val (title, text) = texts(context, alert, zone)
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(
                context,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(
            context,
            if (alert.mode == AlertMode.SOUND) CHANNEL_SOUND else CHANNEL_SILENT
        )
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(
                if (alert.mode == AlertMode.SOUND) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
            )
            .setSilent(alert.mode == AlertMode.SILENT)
            .setWhen(alert.at.toEpochMilli())
            .setShowWhen(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            // One notification per prayer: today's Dhuhr replaces yesterday's.
            notifications.notify(NOTIFICATION_ID_BASE + alert.prayer.ordinal, notification)
            true
        } catch (_: SecurityException) {
            false // permission revoked between the check and the call
        }
    }

    private fun texts(context: Context, alert: AdhanPlan.Alert, zone: ZoneId): Pair<String, String> {
        if (alert.prayer == PrayerName.SUNRISE) {
            return context.getString(R.string.alert_sunrise_title) to context.getString(R.string.alert_sunrise_text)
        }
        val locale = context.resources.configuration.locales[0]
        val numerals = NumeralSystem.forLanguage(locale.toLanguageTag())
        val time = TimeFormatter.format(
            alert.at.atZone(zone).toLocalTime(),
            locale,
            DateFormat.is24HourFormat(context),
            numerals
        )
        val name = context.getString(prayerName(alert.prayer))
        val title = context.getString(R.string.alert_prayer_title, name)
        return title to context.getString(R.string.alert_prayer_text, time)
    }

    private fun prayerName(prayer: PrayerName) = when (prayer) {
        PrayerName.FAJR -> R.string.prayer_fajr
        PrayerName.SUNRISE -> R.string.prayer_sunrise
        PrayerName.DHUHR -> R.string.prayer_dhuhr
        PrayerName.ASR -> R.string.prayer_asr
        PrayerName.MAGHRIB -> R.string.prayer_maghrib
        PrayerName.ISHA -> R.string.prayer_isha
    }
}
