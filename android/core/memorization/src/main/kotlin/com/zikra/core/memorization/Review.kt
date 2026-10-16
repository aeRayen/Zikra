package com.zikra.core.memorization

import java.time.LocalDate
import kotlin.math.roundToInt

// SM-2 recall qualities (0..5): below 3 is a lapse.
private const val QUALITY_FORGOT = 1
private const val QUALITY_HARD = 3
private const val QUALITY_GOOD = 4
private const val QUALITY_EASY = 5

/** How well a page was recited from memory at a review. */
enum class Recall(internal val quality: Int) {
    /** Forgotten: start the page's intervals again from tomorrow. */
    FORGOT(QUALITY_FORGOT),

    /** Recited with effort. */
    HARD(QUALITY_HARD),

    /** Recited well. */
    GOOD(QUALITY_GOOD),

    /** Recited easily. */
    EASY(QUALITY_EASY),
}

/** The review state of one memorized page. Ease is in hundredths (250 = 2.5) so it never drifts. */
data class PageReview(
    val page: Int,
    val memorizedOn: LocalDate,
    val due: LocalDate,
    val intervalDays: Int = 1,
    val easePercent: Int = START_EASE,
    /** Reviews in a row recited at least «hard». */
    val streak: Int = 0,
    val lapses: Int = 0,
    val lastReviewed: LocalDate? = null,
    /** Memorized before the plan (marked by juz'): does not count against the daily pace. */
    val beforePlan: Boolean = false,
) {
    companion object {
        const val START_EASE = 250
        const val MIN_EASE = 130

        /** A page memorized [on] that day is first reviewed the next day. */
        fun memorized(page: Int, on: LocalDate) = PageReview(page, memorizedOn = on, due = on.plusDays(1))
    }
}

/**
 * Spaced review (WP 3.9, ADR-045): the SM-2 schedule. A page recited well comes back after 1 day, then
 * 6 days, then each time after the previous interval times its ease; the ease rises for easy pages
 * and falls (to [PageReview.MIN_EASE] at most) for hard ones. A forgotten page starts again tomorrow.
 */
object SpacedReview {
    private const val SECOND_INTERVAL = 6
    private const val PERFECT = 5
    private const val FORGOTTEN_BELOW = 3
    private const val PERCENT = 100.0

    // SM-2 ease change in hundredths: 0.1, 0.08 and 0.02.
    private const val EASE_BONUS = 10
    private const val EASE_STEP = 8
    private const val EASE_STEP_GROWTH = 2

    fun review(state: PageReview, recall: Recall, today: LocalDate): PageReview {
        val ease = nextEase(state.easePercent, recall.quality)
        if (recall.quality < FORGOTTEN_BELOW) {
            return state.copy(
                intervalDays = 1,
                due = today.plusDays(1),
                easePercent = ease,
                streak = 0,
                lapses = state.lapses + 1,
                lastReviewed = today,
            )
        }
        val streak = state.streak + 1
        val interval = when (streak) {
            1 -> 1
            2 -> SECOND_INTERVAL
            else -> (state.intervalDays * ease / PERCENT).roundToInt().coerceAtLeast(state.intervalDays + 1)
        }
        return state.copy(
            intervalDays = interval,
            due = today.plusDays(interval.toLong()),
            easePercent = ease,
            streak = streak,
            lastReviewed = today,
        )
    }

    /** SM-2: EF' = EF + (0.1 - (5 - q) × (0.08 + (5 - q) × 0.02)), in hundredths. */
    internal fun nextEase(easePercent: Int, quality: Int): Int {
        val miss = PERFECT - quality
        val delta = EASE_BONUS - miss * (EASE_STEP + miss * EASE_STEP_GROWTH)
        return (easePercent + delta).coerceAtLeast(PageReview.MIN_EASE)
    }
}
