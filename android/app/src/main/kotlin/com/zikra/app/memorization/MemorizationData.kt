package com.zikra.app.memorization

import com.zikra.core.database.user.MemorizationDao
import com.zikra.core.database.user.MemorizationPlanEntity
import com.zikra.core.database.user.MemorizedPageEntity
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.Memorization
import com.zikra.core.memorization.MemorizationPlan
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.PageReview
import com.zikra.core.memorization.Recall
import com.zikra.core.memorization.SpacedReview
import com.zikra.core.quran.QuranText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** The plan and the memorized pages, as the scheduling logic sees them. */
data class MemorizationState(val plan: MemorizationPlan?, val pages: List<PageReview>)

/**
 * Memorization in user.db (WP 3.9): the plan and each memorized page with its review state. On the
 * device; synced only with consent, like bookmarks (ADR-024).
 */
class MemorizationData(private val dao: MemorizationDao, private val now: () -> Long = System::currentTimeMillis) {

    val state: Flow<MemorizationState> = combine(dao.observePlan(), dao.observePages()) { plan, pages ->
        MemorizationState(plan?.toPlan(), pages.map { it.toReview() })
    }

    suspend fun setPlan(plan: MemorizationPlan) =
        dao.setPlan(
            MemorizationPlanEntity(direction = plan.direction.name, pace = plan.pace.name, clientUpdatedAt = now()),
        )

    suspend fun memorized(page: Int, today: LocalDate) = save(listOf(PageReview.memorized(page, today)))

    suspend fun reviewed(page: Int, recall: Recall, today: LocalDate) {
        val current = dao.page(page)?.toReview() ?: return
        save(listOf(SpacedReview.review(current, recall, today)))
    }

    /** Pages memorized before the plan; pages already in the plan keep their state. */
    suspend fun alreadyMemorized(pages: List<Int>, today: LocalDate, known: Set<Int>) =
        save(Memorization.alreadyMemorized(pages.filterNot { it in known }, today))

    suspend fun forget(pages: List<Int>) = dao.forget(pages, now())

    private suspend fun save(pages: List<PageReview>) = dao.upsertPages(pages.map { it.toEntity(now()) })
}

private fun MemorizationPlanEntity.toPlan(): MemorizationPlan? {
    val d = Direction.entries.firstOrNull { it.name == direction }
    val p = Pace.entries.firstOrNull { it.name == pace }
    return if (d != null && p != null) MemorizationPlan(d, p) else null
}

private fun MemorizedPageEntity.toReview() = PageReview(
    page = page,
    memorizedOn = LocalDate.parse(memorizedOn),
    due = LocalDate.parse(due),
    intervalDays = intervalDays,
    easePercent = easePercent,
    streak = streak,
    lapses = lapses,
    lastReviewed = lastReviewed?.let(LocalDate::parse),
    beforePlan = beforePlan,
)

private fun PageReview.toEntity(now: Long) = MemorizedPageEntity(
    page = page,
    memorizedOn = memorizedOn.toString(),
    due = due.toString(),
    intervalDays = intervalDays,
    easePercent = easePercent,
    streak = streak,
    lapses = lapses,
    lastReviewed = lastReviewed?.toString(),
    beforePlan = beforePlan,
    clientUpdatedAt = now,
)

/** Where a mushaf page starts, to name it and open it in the reader. */
data class MushafPage(
    val page: Int,
    val surah: Int,
    val ayah: Int,
    val juz: Int,
    val surahNameArabic: String,
    val surahNameLatin: String,
)

/** The 604 pages of the bundled mushaf, read once (6236 ayahs): off the main thread. */
fun mushafPages(quran: QuranText): List<MushafPage> {
    val names = quran.surahs().associateBy { it.number }
    return (1..QuranText.SURAH_COUNT).flatMap { quran.ayahs(it) }
        .groupBy { it.page }
        .map { (page, ayahs) ->
            val first = ayahs.first()
            val surah = names.getValue(first.surah)
            MushafPage(page, first.surah, first.ayah, first.juz, surah.nameArabic, surah.nameTransliterated)
        }
        .sortedBy { it.page }
}
