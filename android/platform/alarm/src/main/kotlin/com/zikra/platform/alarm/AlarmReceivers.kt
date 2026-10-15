package com.zikra.platform.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.os.UserManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs when an alarm of its kind fires (kept alive with goAsync: about 10 s). Post the notification first,
 * then any follow-up such as re-planning. May run before the first unlock: no credential storage then.
 */
fun interface AlarmHandler {
    suspend fun onAlarm(context: Context, key: String)
}

/**
 * Plans the alarms of one feature (e.g. the next adhans). Called after reboot, time / time-zone change,
 * app update and exact-alarm permission change - only once the user has unlocked the phone, because
 * planning reads settings that live in credential-protected storage.
 */
fun interface AlarmPlanner {
    suspend fun replan(context: Context, reason: ReplanReason)
}

enum class ReplanReason { BOOT, TIME_CHANGED, TIME_ZONE_CHANGED, APP_UPDATED, PERMISSION_CHANGED, SETTINGS_CHANGED }

/** Features register their handler and planner here at app start (Application.onCreate). */
object AlarmRegistrations {
    private val handlers = mutableMapOf<AlarmKind, AlarmHandler>()
    private val planners = mutableListOf<AlarmPlanner>()

    @Synchronized
    fun register(kind: AlarmKind, handler: AlarmHandler) {
        handlers[kind] = handler
    }

    @Synchronized
    fun register(planner: AlarmPlanner) {
        if (planner !in planners) planners += planner
    }

    @Synchronized
    internal fun handler(kind: AlarmKind): AlarmHandler? = handlers[kind]

    @Synchronized
    internal fun planners(): List<AlarmPlanner> = planners.toList()

    @Synchronized
    internal fun clearForTest() {
        handlers.clear()
        planners.clear()
    }
}

/** Receives our own alarms (explicit, not exported). Direct-boot aware: alarms ring before the first unlock. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(AlarmScheduler.EXTRA_KEY)
        val kindName = intent.getStringExtra(AlarmScheduler.EXTRA_KIND)
        val kind = AlarmKind.entries.firstOrNull { it.name == kindName }
        if (intent.action != AlarmScheduler.ACTION_FIRE || key == null || kind == null) return
        AlarmScheduler.get(context).onFired(intent.getIntExtra(AlarmScheduler.EXTRA_REQUEST_CODE, -1))
        val handler = AlarmRegistrations.handler(kind) ?: return
        val pending: PendingResult? = goAsync() // null only when called outside a system broadcast
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                handler.onAlarm(context, key)
            } finally {
                pending?.finish()
            }
        }
    }
}

/**
 * System events after which alarms must be re-armed or re-planned (ADR-033). The system drops all alarms
 * on reboot; local-time alarms move when the clock or time zone changes.
 */
class AlarmSystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reason = reasonFor(intent.action) ?: return
        // goAsync: planners read settings from disk; keep the process alive until they finish.
        val pending: PendingResult? = goAsync() // null only when called outside a system broadcast
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                onSystemEvent(context, reason)
            } finally {
                pending?.finish()
            }
        }
    }

    companion object {
        internal fun reasonFor(action: String?): ReplanReason? = when (action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> ReplanReason.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> ReplanReason.APP_UPDATED
            Intent.ACTION_TIME_CHANGED -> ReplanReason.TIME_CHANGED
            Intent.ACTION_TIMEZONE_CHANGED -> ReplanReason.TIME_ZONE_CHANGED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> ReplanReason.PERMISSION_CHANGED
            else -> null
        }

        /** First re-arm what we know (works while locked), then let planners recompute if storage is unlocked. */
        suspend fun onSystemEvent(context: Context, reason: ReplanReason) {
            AlarmScheduler.get(context).rearmAll()
            if (UserManagerCompat.isUserUnlocked(context)) {
                AlarmRegistrations.planners().forEach { it.replan(context, reason) }
            }
        }
    }
}
