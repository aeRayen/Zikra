package com.zikra.core.settings

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * The user's prayer-time choices. Every field null/empty = automatic (regional default for the saved
 * location, ADR-032). Values are stable ids (CalculationMethodId.id, enum names), so an unknown value
 * from a newer app version simply falls back to automatic.
 */
@Serializable
data class PrayerPreferences(
    val version: Int = 1,
    val methodId: String? = null,
    /** "STANDARD" or "HANAFI". */
    val madhab: String? = null,
    /** HighLatitudeRule name; null = engine recommendation for the location. */
    val highLatitudeRule: String? = null,
    /** PrayerName -> minutes (±30). */
    val adjustmentMinutes: Map<String, Int> = emptyMap(),
)

/** prayer.json: not location data, so it may be included in backups. One instance per process. */
class PrayerPreferencesStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val store: DataStore<PrayerPreferences> = DataStoreFactory.create(
        serializer = PrayerPreferencesSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { PrayerPreferences() },
        scope = scope,
        produceFile = produceFile,
    )

    val preferences: Flow<PrayerPreferences> = store.data

    suspend fun update(transform: (PrayerPreferences) -> PrayerPreferences) {
        store.updateData(transform)
    }

    companion object {
        const val FILE_NAME = "prayer.json"
    }
}

internal object PrayerPreferencesSerializer : Serializer<PrayerPreferences> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val defaultValue = PrayerPreferences()

    override suspend fun readFrom(input: InputStream): PrayerPreferences = try {
        json.decodeFromString(PrayerPreferences.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable prayer preferences", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid prayer preferences", e)
    }

    override suspend fun writeTo(t: PrayerPreferences, output: OutputStream) {
        output.write(json.encodeToString(PrayerPreferences.serializer(), t).encodeToByteArray())
    }
}
