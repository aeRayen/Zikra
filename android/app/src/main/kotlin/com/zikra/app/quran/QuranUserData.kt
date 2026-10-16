package com.zikra.app.quran

import com.zikra.core.database.user.BookmarkDao
import com.zikra.core.database.user.BookmarkEntity
import com.zikra.core.database.user.ReadingPositionDao
import com.zikra.core.database.user.ReadingPositionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** An ayah reference, e.g. (2, 255). Stored as item_key "2:255" with item_type "ayah". */
data class AyahRef(val surah: Int, val ayah: Int) {
    val key: String get() = "$surah:$ayah"

    companion object {
        fun parse(key: String): AyahRef? {
            val parts = key.split(':').mapNotNull { it.toIntOrNull() }
            return if (parts.size == 2 && key.count { it == ':' } == 1) AyahRef(parts[0], parts[1]) else null
        }
    }
}

/**
 * Bookmarks and «continue reading» (WP 2.12), in user.db on the device: work offline, and are
 * synced later only with the user's consent (sync.bookmarks / sync.reading, ADR-024).
 */
class QuranUserData(
    private val bookmarks: BookmarkDao,
    private val position: ReadingPositionDao,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** Live ayah bookmarks, newest first. */
    val bookmarked: Flow<List<AyahRef>> = bookmarks.observeLive().map { list ->
        list.filter { it.itemType == ITEM_AYAH }.mapNotNull { AyahRef.parse(it.itemKey) }
    }

    val lastRead: Flow<AyahRef?> = position.observe().map { it?.let { p -> AyahRef(p.surah, p.ayah) } }

    /** Adds the bookmark, or removes it (as a tombstone, so other devices learn about it). */
    suspend fun toggle(ref: AyahRef) {
        val existing = bookmarks.find(ITEM_AYAH, ref.key)
        val time = now()
        when {
            existing == null -> bookmarks.upsert(BookmarkEntity(newId(), ITEM_AYAH, ref.key, time, time))
            existing.deleted -> bookmarks.upsert(
                existing.copy(deleted = false, dirty = true, createdAt = time, clientUpdatedAt = time)
            )
            else -> bookmarks.markDeleted(existing.id, time)
        }
    }

    suspend fun saveLastRead(ref: AyahRef) {
        position.set(ReadingPositionEntity(surah = ref.surah, ayah = ref.ayah, clientUpdatedAt = now()))
    }

    companion object {
        const val ITEM_AYAH = "ayah"
    }
}
