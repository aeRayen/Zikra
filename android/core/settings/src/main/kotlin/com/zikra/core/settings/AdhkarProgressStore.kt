package com.zikra.core.settings

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDate

/** Today's adhkar counters (WP 2.14b): how many times each dhikr was said, by dhikr id. */
@Serializable
data class AdhkarProgress(
    val version: Int = 1,
    /** ISO date the counts belong to; another day means nothing has been said yet. */
    val day: String? = null,
    val counts: Map<Int, Int> = emptyMap(),
)

/**
 * adhkar-progress.json: every tap is saved at once, so closing or killing the app loses nothing.
 * Counts belong to the civil day (it starts at midnight, as the Hijri date does: ADR-035).
 */
class AdhkarProgressStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val today: () -> LocalDate = LocalDate::now,
) : JsonFileStore<AdhkarProgress>(produceFile, AdhkarProgress.serializer(), AdhkarProgress(), scope) {

    val counts: Flow<Map<Int, Int>> = data.map { it.ofToday() }

    /** One more time for [dhikr], never beyond [repeat]. */
    suspend fun count(dhikr: Int, repeat: Int) = update { p ->
        val counts = p.ofToday()
        val next = minOf(repeat, (counts[dhikr] ?: 0) + 1)
        AdhkarProgress(day = today().toString(), counts = counts + (dhikr to next))
    }

    /** Starts [adhkar] again from zero (e.g. a chapter read a second time the same day). */
    suspend fun reset(adhkar: Collection<Int>) = update { p ->
        AdhkarProgress(day = today().toString(), counts = p.ofToday() - adhkar.toSet())
    }

    private fun AdhkarProgress.ofToday(): Map<Int, Int> = if (day == today().toString()) counts else emptyMap()

    companion object {
        const val FILE_NAME = "adhkar-progress.json"
    }
}
