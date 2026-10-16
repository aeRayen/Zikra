package com.zikra.app.ui

import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall

/** What the memorization screen can do; grouped to keep the signature short. */
interface MemorizationActions {
    val onBack: () -> Unit
    val onSetPlan: (Direction, Pace) -> Unit
    val onOpenPage: (surah: Int, ayah: Int) -> Unit
    val onMemorized: (page: Int) -> Unit
    val onReviewed: (page: Int, Recall) -> Unit
    val onMemorizedJuz: (juz: Int) -> Unit
}
