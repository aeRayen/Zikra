package com.zikra.core.packs

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/** installed.json: which version of each edition is installed, and in which file. */
internal class PackRegistry(private val root: File) {
    private val registry = File(root, REGISTRY)
    private val json = Json { ignoreUnknownKeys = true }
    private val registrySerializer = MapSerializer(String.serializer(), InstalledPack.serializer())

    fun read(): Map<String, InstalledPack> =
        if (!registry.isFile) {
            emptyMap()
        } else {
            try {
                json.decodeFromString(registrySerializer, registry.readText())
            } catch (_: IllegalArgumentException) {
                emptyMap() // a damaged registry: packs are downloaded again
            }
        }

    /** Atomic: written next to it, then renamed over it. */
    fun write(packs: Map<String, InstalledPack>) {
        val tmp = File(root, "$REGISTRY.tmp")
        tmp.writeText(json.encodeToString(registrySerializer, packs))
        if (!tmp.renameTo(registry)) {
            registry.delete()
            if (!tmp.renameTo(registry)) throw IOException("Cannot write $REGISTRY")
        }
    }
    private companion object {
        const val REGISTRY = "installed.json"
    }
}
