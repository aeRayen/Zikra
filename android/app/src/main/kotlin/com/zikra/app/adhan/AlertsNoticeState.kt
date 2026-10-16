package com.zikra.app.adhan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.zikra.app.ui.AlertsNotice
import com.zikra.platform.alarm.AlarmScheduler
import com.zikra.platform.alarm.DeliveryMode

/** Why alerts may not reach the user, and the one action that fixes it (prayer alerts, adhkar reminders). */
class AlertsNoticeState(val notice: AlertsNotice, val fix: () -> Unit)

/** Re-checked on every resume: the user may come back from the system settings. */
@Composable
fun rememberAlertsNotice(): AlertsNoticeState {
    val context = LocalContext.current
    var notice by remember { mutableStateOf(AlertsNotice.NONE) }
    var askedForNotifications by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        notice = alertsNotice(context)
        onPauseOrDispose {}
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        askedForNotifications = true
        notice = alertsNotice(context)
    }
    return AlertsNoticeState(notice) {
        when (notice) {
            AlertsNotice.NEED_NOTIFICATION_PERMISSION ->
                // The system dialog appears at most twice; after that (or below Android 13) only the
                // app's notification settings can turn them on.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !askedForNotifications) {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    context.startActivity(notificationSettings(context))
                }
            // Granting it broadcasts SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED: alarms re-arm then.
            AlertsNotice.INEXACT_DELIVERY ->
                AlarmScheduler.exactAlarmSettingsIntent(context)?.let(context::startActivity)
            AlertsNotice.NONE -> Unit
        }
    }
}

/** Notifications first (nothing shows without them), then on-time delivery. */
private fun alertsNotice(context: Context): AlertsNotice = when {
    !NotificationManagerCompat.from(context).areNotificationsEnabled() -> AlertsNotice.NEED_NOTIFICATION_PERMISSION
    AlarmScheduler.get(context).deliveryMode() == DeliveryMode.INEXACT -> AlertsNotice.INEXACT_DELIVERY
    else -> AlertsNotice.NONE
}

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
