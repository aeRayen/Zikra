package com.zikra.app.adhkar

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.zikra.app.MainActivity
import com.zikra.app.R
import java.time.Duration
import java.time.Instant

/**
 * Posts adhkar reminders (WP 2.14c). Uses only resources and the alarm key, never credential storage, so
 * it also works before the first unlock after a reboot. Tapping it opens the chapter.
 */
object AdhkarReminderNotifier {
    const val CHANNEL = "adhkar_reminders"
    const val EXTRA_CHAPTER = "com.zikra.app.ADHKAR_CHAPTER"

    /** A reminder hours late (phone off, Doze) is no longer timely: the evening reminder at night. */
    val STALE_AFTER: Duration = Duration.ofHours(2)
    private const val NOTIFICATION_ID_BASE = 3_000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_adhkar),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_adhkar_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Returns false when nothing was posted (stale, or notifications not allowed). */
    fun post(context: Context, reminder: AdhkarReminderPlan.Reminder, now: Instant): Boolean {
        val notifications = NotificationManagerCompat.from(context)
        if (Duration.between(reminder.at, now) > STALE_AFTER || !notifications.areNotificationsEnabled()) return false
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_BASE + reminder.kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CHAPTER, reminder.kind.chapter)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val (title, text) = when (reminder.kind) {
            AdhkarReminderKind.MORNING -> R.string.adhkar_reminder_morning to R.string.adhkar_reminder_morning_text
            AdhkarReminderKind.EVENING -> R.string.adhkar_reminder_evening to R.string.adhkar_reminder_evening_text
            AdhkarReminderKind.SLEEP -> R.string.adhkar_reminder_sleep to R.string.adhkar_reminder_sleep_text
        }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_prayer)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return try {
            // One notification per kind: today's morning reminder replaces yesterday's.
            notifications.notify(NOTIFICATION_ID_BASE + reminder.kind.ordinal, notification)
            true
        } catch (_: SecurityException) {
            false // permission revoked between the check and the call
        }
    }
}
