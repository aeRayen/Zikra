package com.zikra.core.database.content

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert

/** Licensed source of a content pack; shown on «About this text» (CONTENT_PACKS_AR.md). */
@Entity(tableName = "source")
data class SourceEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "license_code") val licenseCode: String,
    @ColumnInfo(name = "attribution_text") val attributionText: String?,
    @ColumnInfo(name = "source_url") val sourceUrl: String?,
)

/** A content pack that passed signature + checksum verification and was installed. */
@Entity(tableName = "installed_pack")
data class InstalledPackEntity(
    @PrimaryKey @ColumnInfo(name = "pack_id") val packId: String,
    val area: String,
    val language: String?,
    @ColumnInfo(name = "edition_code") val editionCode: String?,
    val version: String,
    @ColumnInfo(name = "manifest_sha256") val manifestSha256: String,
    @ColumnInfo(name = "installed_at") val installedAt: Long,
)

@Dao
interface InstalledPackDao {
    @Upsert
    suspend fun record(pack: InstalledPackEntity)

    @Query("SELECT * FROM installed_pack WHERE area = :area")
    suspend fun byArea(area: String): List<InstalledPackEntity>
}

/**
 * content.db — read-only religious content, written only by the content-pack installer
 * (LOCAL_SCHEMA_AR.md §1). Quran, translation, tafsir and adhkar tables arrive with Phase 2.
 */
@Database(entities = [SourceEntity::class, InstalledPackEntity::class], version = 1, exportSchema = true)
abstract class ContentDatabase : RoomDatabase() {
    abstract fun installedPackDao(): InstalledPackDao

    companion object {
        const val FILE_NAME = "content.db"
    }
}
