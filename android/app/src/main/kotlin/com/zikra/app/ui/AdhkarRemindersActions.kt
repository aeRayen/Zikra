package com.zikra.app.ui

/** What the adhkar reminders screen can do; grouped to keep the signature short. */
interface AdhkarRemindersActions {
    val onBack: () -> Unit
    val onMorning: (Boolean) -> Unit
    val onEvening: (Boolean) -> Unit
    val onSleep: (Boolean) -> Unit
    val onSleepTime: (hour: Int, minute: Int) -> Unit
    val onChooseLocation: () -> Unit
    val onFixAlerts: () -> Unit
}
