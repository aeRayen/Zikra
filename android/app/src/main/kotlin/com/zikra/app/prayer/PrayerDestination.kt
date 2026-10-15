package com.zikra.app.prayer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.AlertsNotice
import com.zikra.app.ui.PrayerTimesScreen
import com.zikra.platform.alarm.AlarmScheduler
import com.zikra.platform.alarm.DeliveryMode
import kotlinx.coroutines.Dispatchers

/** Navigation entry of the prayer tab. */
@Composable
fun PrayerDestination(onChooseLocation: () -> Unit, onOpenSettings: () -> Unit, onOpenMonth: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel: PrayerTimesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PrayerTimesViewModel(
                    places = { container.places },
                    locationStore = container.locationStore,
                    preferencesStore = container.prayerPreferences,
                    io = Dispatchers.IO,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }

    // Re-checked on every resume: the user may come back from the system settings.
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
    PrayerTimesScreen(
        state = state,
        onChooseLocation = onChooseLocation,
        onOpenSettings = onOpenSettings,
        onOpenMonth = onOpenMonth,
        alertsNotice = notice,
        onFixAlerts = {
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
                AlertsNotice.INEXACT_DELIVERY -> AlarmScheduler.exactAlarmSettingsIntent(
                    context
                )?.let(context::startActivity)
                AlertsNotice.NONE -> Unit
            }
        },
    )
}

/** Notifications first (nothing shows without them), then on-time delivery. */
private fun alertsNotice(context: Context): AlertsNotice = when {
    !NotificationManagerCompat.from(context).areNotificationsEnabled() -> AlertsNotice.NEED_NOTIFICATION_PERMISSION
    AlarmScheduler.get(context).deliveryMode() == DeliveryMode.INEXACT -> AlertsNotice.INEXACT_DELIVERY
    else -> AlertsNotice.NONE
}

private fun notificationSettings(context: Context): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
