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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Typed JSON DataStore (ADR-031): atomic writes, a damaged file falls back to [default], unknown
 * fields from a newer version are ignored. One instance per file per process.
 */
open class JsonFileStore<T>(
    produceFile: () -> File,
    serializer: KSerializer<T>,
    default: T,
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val store: DataStore<T> = DataStoreFactory.create(
        serializer = JsonSerializer(serializer, default),
        corruptionHandler = ReplaceFileCorruptionHandler { default },
        scope = scope,
        produceFile = produceFile,
    )

    val data: Flow<T> = store.data

    suspend fun update(transform: (T) -> T) {
        store.updateData(transform)
    }
}

private class JsonSerializer<T>(private val serializer: KSerializer<T>, override val defaultValue: T) : Serializer<T> {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override suspend fun readFrom(input: InputStream): T = try {
        json.decodeFromString(serializer, input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable settings file", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Invalid settings file", e)
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(json.encodeToString(serializer, t).encodeToByteArray())
    }
}
