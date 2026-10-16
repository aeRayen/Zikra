package com.zikra.app.ui

import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall

internal const val PLAN_CARD_TAG = "memorization_plan"
internal const val PLAN_SAVE_TAG = "memorization_plan_save"
internal fun paceTag(pace: Pace) = "memorization_pace_${pace.name}"
internal fun newPageTag(page: Int) = "memorization_new_$page"
internal fun memorizedTag(page: Int) = "memorization_memorized_$page"
internal fun reviewTag(page: Int) = "memorization_review_$page"
internal fun recallTag(page: Int, recall: Recall) = "memorization_recall_${page}_${recall.name}"
internal fun juzTag(juz: Int) = "memorization_juz_$juz"
internal fun markJuzTag(juz: Int) = "memorization_mark_juz_$juz"
