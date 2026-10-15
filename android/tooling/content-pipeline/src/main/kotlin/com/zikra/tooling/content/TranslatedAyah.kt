package com.zikra.tooling.content

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

data class TranslatedAyah(val surah: Int, val ayah: Int, val text: String, val footnotes: String?)

@Serializable
private data class QuranEncFile(
    val key: String,
    val title: String,
    val version: String,
    val source: String,
    @SerialName("source_url") val sourceUrl: String,
    val retrieved: String,
    val ayahs: List<QuranEncAyah>,
)

@Serializable
private data class QuranEncAyah(val sura: Int, val aya: Int, val translation: String, val footnotes: String? = null)

/** Reads the combined QuranEnc file produced at download time (raw API responses merged in order). */
fun parseQuranEnc(file: File, expectedVersion: String): List<TranslatedAyah> {
    val parsed = lenientJson.decodeFromString(QuranEncFile.serializer(), file.readText())
    if (parsed.version != expectedVersion) {
        throw PipelineException("QuranEnc version ${parsed.version} does not match pinned $expectedVersion")
    }
    return parsed.ayahs.map { TranslatedAyah(it.sura, it.aya, it.translation, it.footnotes?.ifBlank { null }) }
}

private val lenientJson = Json { ignoreUnknownKeys = true }
