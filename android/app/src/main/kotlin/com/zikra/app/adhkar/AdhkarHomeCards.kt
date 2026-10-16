package com.zikra.app.adhkar

import com.zikra.app.home.HomeCard
import com.zikra.app.home.HomeCardProvider
import com.zikra.core.adhkar.AdhkarBook
import com.zikra.core.adhkar.AdhkarChapter
import com.zikra.core.adhkar.AdhkarIntegrityException
import com.zikra.core.adhkar.Dhikr
import com.zikra.core.settings.AdhkarProgressStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Morning and evening adhkar on «Today», with how many are done today (WP 2.14b). */
class AdhkarHomeCards(
    private val book: () -> AdhkarBook,
    private val progress: AdhkarProgressStore,
    private val io: CoroutineDispatcher,
) : HomeCardProvider {
    override fun cards(language: Flow<String>): Flow<List<HomeCard>> {
        val chapter = flow {
            val loaded = try {
                val b = book()
                val id = AdhkarBook.MORNING_EVENING
                b.chapters().firstOrNull { it.id == id }?.let { it to b.chapter(id) }
            } catch (_: AdhkarIntegrityException) {
                null
            }
            emit(loaded)
        }.flowOn(io)
        return combine(chapter, progress.counts) { loaded: Pair<AdhkarChapter, List<Dhikr>>?, counts ->
            if (loaded == null) {
                emptyList()
            } else {
                val (c, adhkar) = loaded
                listOf(HomeCard.DailyAdhkar(c, adhkar.count { (counts[it.id] ?: 0) >= it.repeat }))
            }
        }
    }
}
