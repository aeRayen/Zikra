package com.zikra.core.memorization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** WP 3.9 acceptance: the scheduling logic (SM-2 and the daily agenda). */
class MemorizationTest {

    private val day = LocalDate.of(2026, 10, 16)

    @Test
    fun aNewPageIsFirstReviewedTomorrow() {
        val page = PageReview.memorized(604, day)
        assertEquals(day.plusDays(1), page.due)
        assertEquals(PageReview.START_EASE, page.easePercent)
    }

    @Test
    fun goodRecallsGrowTheIntervalOneSixThenTimesTheEase() {
        var page = PageReview.memorized(1, day)
        val intervals = mutableListOf<Int>()
        var today = page.due
        repeat(4) {
            page = SpacedReview.review(page, Recall.GOOD, today)
            intervals += page.intervalDays
            assertEquals(today.plusDays(page.intervalDays.toLong()), page.due)
            today = page.due
        }
        // «Good» keeps the ease at 2.5: 1, 6, 15, 38 (15 × 2.5 = 37.5, rounded).
        assertEquals(listOf(1, 6, 15, 38), intervals)
        assertEquals(250, page.easePercent)
    }

    @Test
    fun easeFollowsSm2AndNeverFallsBelowItsFloor() {
        assertEquals(260, SpacedReview.nextEase(250, Recall.EASY.quality))
        assertEquals(250, SpacedReview.nextEase(250, Recall.GOOD.quality))
        assertEquals(236, SpacedReview.nextEase(250, Recall.HARD.quality))
        assertEquals(196, SpacedReview.nextEase(250, Recall.FORGOT.quality))
        assertEquals(PageReview.MIN_EASE, SpacedReview.nextEase(140, Recall.FORGOT.quality))
    }

    @Test
    fun aForgottenPageStartsAgainTomorrowAndCountsALapse() {
        var page = PageReview.memorized(2, day)
        page = SpacedReview.review(page, Recall.GOOD, day.plusDays(1))
        page = SpacedReview.review(page, Recall.GOOD, day.plusDays(2))
        assertEquals(6, page.intervalDays)
        val today = day.plusDays(8)
        page = SpacedReview.review(page, Recall.FORGOT, today)
        assertEquals(today.plusDays(1), page.due)
        assertEquals(0, page.streak)
        assertEquals(1, page.lapses)
        // Then it climbs again from the start.
        page = SpacedReview.review(page, Recall.GOOD, today.plusDays(1))
        assertEquals(1, page.intervalDays)
    }

    @Test
    fun aHardPageStillGrowsButSlower() {
        var page = PageReview.memorized(3, day)
        page = SpacedReview.review(page, Recall.HARD, day.plusDays(1))
        page = SpacedReview.review(page, Recall.HARD, day.plusDays(2))
        page = SpacedReview.review(page, Recall.HARD, day.plusDays(8))
        assertTrue(page.intervalDays in 7..14)
        assertTrue(page.easePercent < PageReview.START_EASE)
    }

    @Test
    fun theAgendaFollowsTheDirectionAndThePace() {
        val fromEnd = MemorizationPlan(Direction.FROM_END, Pace.ONE_PAGE)
        assertEquals(listOf(604), Memorization.agenda(fromEnd, emptyList(), day).newPages)
        val fromStart = MemorizationPlan(Direction.FROM_START, Pace.TWO_PAGES)
        assertEquals(listOf(1, 2), Memorization.agenda(fromStart, emptyList(), day).newPages)
        // One already memorized today: one more allowed.
        val one = listOf(PageReview.memorized(1, day))
        assertEquals(listOf(2), Memorization.agenda(fromStart, one, day).newPages)
        // One page per day: nothing more today, the next page tomorrow.
        val daily = MemorizationPlan(Direction.FROM_START, Pace.ONE_PAGE)
        assertTrue(Memorization.agenda(daily, one, day).newPages.isEmpty())
        assertEquals(listOf(2), Memorization.agenda(daily, one, day.plusDays(1)).newPages)
    }

    @Test
    fun halfAPageADayMeansOnePageEveryOtherDay() {
        val plan = MemorizationPlan(Direction.FROM_START, Pace.HALF_PAGE)
        val one = listOf(PageReview.memorized(1, day))
        assertTrue(Memorization.agenda(plan, one, day.plusDays(1)).newPages.isEmpty())
        assertEquals(listOf(2), Memorization.agenda(plan, one, day.plusDays(2)).newPages)
    }

    @Test
    fun reviewsAreTheDuePagesMostOverdueFirst() {
        val pages = listOf(
            PageReview(10, day, due = day),
            PageReview(5, day, due = day.minusDays(3)),
            PageReview(7, day, due = day.plusDays(1)),
        )
        assertEquals(listOf(5, 10), Memorization.agenda(null, pages, day).reviews.map { it.page })
    }

    @Test
    fun pagesMemorizedBeforeThePlanAreSpreadAndDoNotUseThePace() {
        val juz30 = (582..604).toList()
        val before = Memorization.alreadyMemorized(juz30, day)
        assertEquals(juz30.size, before.size)
        val last = day.plusDays(Memorization.SPREAD_DAYS.toLong())
        assertTrue(before.all { it.due.isAfter(day) && !it.due.isAfter(last) })
        assertTrue(before.groupBy { it.due }.values.all { it.size <= 2 })
        // The plan from the end continues with the first page not yet memorized, today.
        val plan = MemorizationPlan(Direction.FROM_END, Pace.ONE_PAGE)
        assertEquals(listOf(581), Memorization.agenda(plan, before, day).newPages)
    }

    @Test
    fun aFinishedPlanHasNoNewPages() {
        val all = Memorization.alreadyMemorized((1..Memorization.PAGE_COUNT).toList(), day)
        val plan = MemorizationPlan(Direction.FROM_START, Pace.TWO_PAGES)
        assertTrue(Memorization.agenda(plan, all, day).newPages.isEmpty())
    }
}
