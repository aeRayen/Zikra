package com.zikra.core.database.user

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * user.db — the user's own, non-sensitive data (bookmarks, reading position, goals, memorization).
 * Not encrypted; included in Android backup (LOCAL_SCHEMA_AR.md §2). Every schema change needs a Migration and a
 * migration test; destructive fallback is forbidden.
 */
@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkNoteEntity::class,
        ReadingPositionEntity::class,
        WorshipGoalEntity::class,
        WorshipProgressEntity::class,
        DhikrCounterEntity::class,
        SyncOutboxEntity::class,
        SyncStateEntity::class,
        MemorizationPlanEntity::class,
        MemorizedPageEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2 (WP 3.9): two new tables, nothing else changes: an automatic migration, tested in SchemaContractTest.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun worshipDao(): WorshipDao

    abstract fun syncOutboxDao(): SyncOutboxDao

    abstract fun memorizationDao(): MemorizationDao

    companion object {
        const val FILE_NAME = "user.db"
    }
}
