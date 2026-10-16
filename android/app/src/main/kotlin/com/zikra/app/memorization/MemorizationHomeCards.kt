package com.zikra.app.memorization

import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeCardProvider
import com.zikra.core.memorization.Memorization
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Today's memorization on «Today» (WP 3.9): shown once there is a plan or a memorized page. */
class MemorizationHomeCards(
    private val data: () -> MemorizationData,
    private val io: CoroutineDispatcher,
    private val today: () -> LocalDate = LocalDate::now,
) : HomeCardProvider {
    override fun cards(language: Flow<String>): Flow<List<HomeCard>> = data().state.map { state ->
        if (state.plan == null && state.pages.isEmpty()) {
            emptyList()
        } else {
            val agenda = Memorization.agenda(state.plan, state.pages, today())
            listOf(HomeCard.MemorizationToday(agenda.newPages.size, agenda.reviews.size))
        }
    }.flowOn(io)
}
