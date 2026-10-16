package com.zikra.core.memorization

import java.time.LocalDate

/** Where memorizing starts. */
enum class Direction {
    /** From al-Fatiha: page 1 onwards. */
    FROM_START,

    /** From the end of the mushaf (juz' 'Amma first, as many begin): page 604 backwards. */
    FROM_END,
}

/** How many new pages a day. */
enum class Pace {
    /** One page every two days. */
    HALF_PAGE,
    ONE_PAGE,
    TWO_PAGES,
}

data class MemorizationPlan(val direction: Direction, val pace: Pace)

/** What to do today: new pages to memorize, and memorized pages due for review (most overdue first). */
data class Agenda(val newPages: List<Int>, val reviews: List<PageReview>)

/**
 * The daily memorization agenda (WP 3.9, ADR-045). Pure: the caller passes the plan, the memorized pages
 * and today's date (the civil day, as everywhere in the app: ADR-035).
 */
object Memorization {
    const val PAGE_COUNT = 604

    /** Every page in the plan's order. */
    fun order(direction: Direction): List<Int> = when (direction) {
        Direction.FROM_START -> (1..PAGE_COUNT).toList()
        Direction.FROM_END -> (PAGE_COUNT downTo 1).toList()
    }

    fun agenda(plan: MemorizationPlan?, memorized: List<PageReview>, today: LocalDate): Agenda {
        val reviews = memorized.filter { !it.due.isAfter(today) }.sortedWith(compareBy({ it.due }, { it.page }))
        if (plan == null) return Agenda(emptyList(), reviews)
        val done = memorized.mapTo(HashSet()) { it.page }
        val next = order(plan.direction).filterNot { it in done }
        return Agenda(next.take(newAllowed(plan.pace, memorized, today)), reviews)
    }

    /** New pages still allowed today: the pace counts what was already memorized today (and yesterday). */
    internal fun newAllowed(pace: Pace, memorized: List<PageReview>, today: LocalDate): Int {
        val inPlan = memorized.filterNot { it.beforePlan }
        val todayCount = inPlan.count { it.memorizedOn == today }
        return when (pace) {
            Pace.ONE_PAGE -> 1 - todayCount
            Pace.TWO_PAGES -> 2 - todayCount
            Pace.HALF_PAGE -> if (inPlan.any { !it.memorizedOn.isBefore(today.minusDays(1)) }) 0 else 1
        }.coerceAtLeast(0)
    }

    /**
     * Pages memorized before the plan (e.g. a whole juz'): their first reviews are spread over
     * [SPREAD_DAYS] days so that they do not all fall due tomorrow.
     */
    fun alreadyMemorized(pages: List<Int>, today: LocalDate): List<PageReview> =
        pages.sorted().mapIndexed { i, page ->
            PageReview(page, memorizedOn = today, due = today.plusDays(1L + i % SPREAD_DAYS), beforePlan = true)
        }

    const val SPREAD_DAYS = 14
}
