package com.zikra.core.database.user

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Upsert
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("SELECT * FROM bookmark WHERE item_type = :itemType AND item_key = :itemKey")
    suspend fun find(itemType: String, itemKey: String): BookmarkEntity?

    /** Live bookmarks only (tombstones are kept for sync but hidden). */
    @Query("SELECT * FROM bookmark WHERE deleted = 0 ORDER BY created_at DESC")
    fun observeLive(): Flow<List<BookmarkEntity>>

    /** Soft delete: keeps a tombstone so other devices learn about the deletion. */
    @Query("UPDATE bookmark SET deleted = 1, dirty = 1, client_updated_at = :now WHERE id = :id")
    suspend fun markDeleted(id: String, now: Long)

    @Upsert
    suspend fun upsertNote(note: BookmarkNoteEntity)

    @Query("SELECT * FROM bookmark_note WHERE bookmark_id = :bookmarkId")
    suspend fun note(bookmarkId: String): BookmarkNoteEntity?
}

@Dao
interface ReadingPositionDao {
    @Upsert
    suspend fun set(position: ReadingPositionEntity)

    @Query("SELECT * FROM reading_position WHERE id = 'current'")
    fun observe(): Flow<ReadingPositionEntity?>
}

@Dao
interface WorshipDao {
    @Upsert
    suspend fun upsertGoal(goal: WorshipGoalEntity)

    @Query("DELETE FROM worship_goal WHERE id = :id")
    suspend fun deleteGoalPermanently(id: String)

    @Upsert
    suspend fun upsertProgress(progress: WorshipProgressEntity)

    @Query("SELECT * FROM worship_progress WHERE goal_id = :goalId AND local_date = :localDate AND deleted = 0")
    suspend fun progress(goalId: String, localDate: String): WorshipProgressEntity?

    @Query("SELECT COUNT(*) FROM worship_progress WHERE goal_id = :goalId")
    suspend fun progressCount(goalId: String): Int
}

@Dao
interface SyncOutboxDao {
    @Upsert
    suspend fun enqueue(item: SyncOutboxEntity)

    @Query("SELECT * FROM sync_outbox ORDER BY seq ASC LIMIT :limit")
    suspend fun peek(limit: Int): List<SyncOutboxEntity>

    @Query("DELETE FROM sync_outbox WHERE seq = :seq")
    suspend fun remove(seq: Long)

    @Query("UPDATE sync_outbox SET attempts = attempts + 1, last_error = :errorCode WHERE seq = :seq")
    suspend fun recordFailure(seq: Long, errorCode: String)
}

@Dao
interface MemorizationDao {
    @Upsert
    suspend fun setPlan(plan: MemorizationPlanEntity)

    @Query("SELECT * FROM memorization_plan WHERE id = 'current'")
    fun observePlan(): Flow<MemorizationPlanEntity?>

    @Upsert
    suspend fun upsertPages(pages: List<MemorizedPageEntity>)

    /** Live pages only (tombstones are kept for sync but hidden). */
    @Query("SELECT * FROM memorized_page WHERE deleted = 0 ORDER BY page")
    fun observePages(): Flow<List<MemorizedPageEntity>>

    @Query("SELECT * FROM memorized_page WHERE page = :page AND deleted = 0")
    suspend fun page(page: Int): MemorizedPageEntity?

    /** «Not memorized after all»: a tombstone, like bookmarks. */
    @Query("UPDATE memorized_page SET deleted = 1, dirty = 1, client_updated_at = :now WHERE page IN (:pages)")
    suspend fun forget(pages: List<Int>, now: Long)
}
