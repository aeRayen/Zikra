package com.zikra.app.ui

import com.zikra.core.adhkar.Dhikr

/** What the adhkar chapter screen can do; grouped to keep the screen's signature short. */
interface AdhkarChapterActions {
    val onBack: () -> Unit
    val onCount: (Dhikr) -> Unit
    val onReset: () -> Unit
}
