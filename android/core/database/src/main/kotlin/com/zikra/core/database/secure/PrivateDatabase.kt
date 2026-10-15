package com.zikra.core.database.secure

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Small key/value store for private settings (e.g. app-lock options in Phase 5). */
@Entity(tableName = "private_meta")
data class PrivateMetaEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface PrivateMetaDao {
    @Upsert
    suspend fun put(entry: PrivateMetaEntity)

    @Query("SELECT value FROM private_meta WHERE `key` = :key")
    suspend fun get(key: String): String?
}

/**
 * private.db — recovery and protection data (ADR-011). Encrypted with SQLCipher using a key that
 * never leaves the device (DatabaseKeyProvider), excluded from backup and device transfer
 * (data_extraction_rules.xml). Recovery/protection tables are added in Phases 4–5 via migrations.
 * Never opened with a plain SQLite helper; always through [com.zikra.core.database.ZikraDatabases].
 */
@Database(entities = [PrivateMetaEntity::class], version = 1, exportSchema = true)
abstract class PrivateDatabase : RoomDatabase() {
    abstract fun metaDao(): PrivateMetaDao

    companion object {
        const val FILE_NAME = "private.db"
    }
}
