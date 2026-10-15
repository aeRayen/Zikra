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
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Where a saved location came from. */
@Serializable
enum class LocationSource {
    /** Picked from the city list: works without any permission. */
    @SerialName("city")
    CITY,

    /** From the device's APPROXIMATE location (ACCESS_COARSE_LOCATION only, ADR-018). */
    @SerialName("device")
    DEVICE,
}

/**
 * The location prayer times are computed for. Stays on the device; if settings sync is enabled later,
 * only [cityId] or coordinates rounded to 0.1° are sent (ADR-018, LOCAL_SCHEMA_AR.md).
 * [cityId] is the GeoNames id: the city itself, or the nearest city for a device location (may be null).
 */
@Serializable
data class SavedLocation(
    val source: LocationSource,
    val cityId: Long?,
    val latitude: Double,
    val longitude: Double,
    /** IANA time zone id of the place, e.g. "Asia/Riyadh". */
    val timeZone: String,
    val savedAtEpochMs: Long,
)

@Serializable
internal data class LocationFile(val version: Int = 1, val location: SavedLocation? = null)

/**
 * Stores the chosen location in its own file, which the app excludes from cloud backup. One instance
 * per file per process (DataStore requirement): create it once in the app container.
 */
class LocationStore(
    produceFile: () -> File,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val store: DataStore<LocationFile> = DataStoreFactory.create(
        serializer = LocationSerializer,
        // A damaged file must never crash the app: start over without a location (the user picks again).
        corruptionHandler = ReplaceFileCorruptionHandler { LocationFile() },
        scope = scope,
        produceFile = produceFile,
    )

    val location: Flow<SavedLocation?> = store.data.map { it.location }

    suspend fun save(location: SavedLocation) {
        store.updateData { it.copy(location = location) }
    }

    suspend fun clear() {
        store.updateData { it.copy(location = null) }
    }

    companion object {
        const val FILE_NAME = "location.json"
    }
}

internal object LocationSerializer : Serializer<LocationFile> {
    private val json = Json { ignoreUnknownKeys = true }

    override val defaultValue = LocationFile()

    override suspend fun readFrom(input: InputStream): LocationFile = try {
        json.decodeFromString(LocationFile.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable location file", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid location file", e)
    }

    override suspend fun writeTo(t: LocationFile, output: OutputStream) {
        output.write(json.encodeToString(LocationFile.serializer(), t).encodeToByteArray())
    }
}
