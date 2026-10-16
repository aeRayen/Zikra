package com.zikra.core.database.user

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * user.db (LOCAL_SCHEMA_AR.md §2). Synced tables carry: client-generated UUID [id],
 * clientUpdatedAt (epoch ms), serverVersion (null until the server acknowledges), a tombstone
 * flag [deleted] and a [dirty] flag (changed locally, not yet pushed). Sync only runs for users
 * who signed in and granted the category consent (ADR-024).
 */

@Entity(
    tableName = "bookmark",
    indices = [Index(value = ["item_type", "item_key"], unique = true)],
)
data class BookmarkEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "item_type") val itemType: String,
    @ColumnInfo(name = "item_key") val itemKey: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Personal note on a bookmark. Local-only: never synced (ADR-012). */
@Entity(
    tableName = "bookmark_note",
    foreignKeys = [
        ForeignKey(
            entity = BookmarkEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookmark_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookmarkNoteEntity(
    @PrimaryKey @ColumnInfo(name = "bookmark_id") val bookmarkId: String,
    val text: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

/** «Continue reading»: a single row with [id] = [CURRENT]. */
@Entity(tableName = "reading_position")
data class ReadingPositionEntity(
    @PrimaryKey val id: String = CURRENT,
    val surah: Int,
    val ayah: Int,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val dirty: Boolean = true,
) {
    companion object {
        const val CURRENT = "current"
    }
}

@Entity(tableName = "worship_goal")
data class WorshipGoalEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_type") val goalType: String,
    val title: String?,
    @ColumnInfo(name = "target_value") val targetValue: Int?,
    val period: String,
    val archived: Boolean = false,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "worship_progress",
    foreignKeys = [
        ForeignKey(
            entity = WorshipGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goal_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["goal_id", "local_date"], unique = true)],
)
data class WorshipProgressEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "goal_id") val goalId: String,
    /** ISO-8601 local date (yyyy-MM-dd) in the user's time zone. */
    @ColumnInfo(name = "local_date") val localDate: String,
    val value: Int,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)

/** Dhikr counter per day. Local-only. */
@Entity(tableName = "dhikr_counter", primaryKeys = ["dhikr_id", "local_date"])
data class DhikrCounterEntity(
    @ColumnInfo(name = "dhikr_id") val dhikrId: String,
    @ColumnInfo(name = "local_date") val localDate: String,
    val count: Int,
)

/** Pending changes to push, in order (SYNC_PROTOCOL_AR.md §3). */
@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val entity: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val op: String,
    @ColumnInfo(name = "idempotency_key") val idempotencyKey: String,
    val attempts: Int = 0,
    @ColumnInfo(name = "last_error") val lastError: String? = null,
)

/** Key/value sync state (pull cursor, last sync time). */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val key: String,
    val value: String,
)

/** The memorization plan (WP 3.9): a single row with [id] = [CURRENT]. */
@Entity(tableName = "memorization_plan")
data class MemorizationPlanEntity(
    @PrimaryKey val id: String = CURRENT,
    /** FROM_START or FROM_END. */
    val direction: String,
    /** HALF_PAGE, ONE_PAGE or TWO_PAGES. */
    val pace: String,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val dirty: Boolean = true,
) {
    companion object {
        const val CURRENT = "current"
    }
}

/** A memorized mushaf page and its spaced-review state (WP 3.9). Dates are ISO local dates. */
@Entity(tableName = "memorized_page")
data class MemorizedPageEntity(
    @PrimaryKey val page: Int,
    @ColumnInfo(name = "memorized_on") val memorizedOn: String,
    val due: String,
    @ColumnInfo(name = "interval_days") val intervalDays: Int,
    @ColumnInfo(name = "ease_percent") val easePercent: Int,
    val streak: Int,
    val lapses: Int,
    @ColumnInfo(name = "last_reviewed") val lastReviewed: String?,
    @ColumnInfo(name = "before_plan") val beforePlan: Boolean,
    @ColumnInfo(name = "client_updated_at") val clientUpdatedAt: Long,
    @ColumnInfo(name = "server_version") val serverVersion: Long? = null,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)
