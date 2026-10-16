package com.zikra.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Adhkar reminders (WP 2.14c), all off until the user turns them on. Morning and evening follow the
 * prayer times of the saved location; sleep is at a time the user picks.
 */
@Serializable
data class AdhkarReminders(
    val version: Int = 1,
    val morning: Boolean = false,
    val evening: Boolean = false,
    val sleep: Boolean = false,
    /** Minutes after midnight, local time. */
    val sleepMinutes: Int = DEFAULT_SLEEP_MINUTES,
) {
    val anyOn: Boolean get() = morning || evening || sleep

    companion object {
        const val DEFAULT_SLEEP_MINUTES = 22 * 60
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** adhkar-reminders.json: preferences only; may be backed up. */
class AdhkarRemindersStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : JsonFileStore<AdhkarReminders>(produceFile, AdhkarReminders.serializer(), AdhkarReminders(), scope) {
    companion object {
        const val FILE_NAME = "adhkar-reminders.json"
    }
}
