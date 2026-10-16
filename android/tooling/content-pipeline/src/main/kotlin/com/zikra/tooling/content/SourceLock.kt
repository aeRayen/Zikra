package com.zikra.tooling.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

class PipelineException(message: String) : RuntimeException(message)

@Serializable
data class PinnedSource(
    val id: String,
    val path: String,
    val sha256: String,
    val version: String,
    val publisher: String,
    val url: String,
    val license: String,
    val retrieved: String,
)

@Serializable
data class SourceLock(val comment: String = "", val sources: List<PinnedSource>) {

    fun source(id: String): PinnedSource =
        sources.firstOrNull { it.id == id }
            ?: throw PipelineException("Source '$id' is not pinned in SOURCES.lock.json")

    /**
     * Returns the file of a pinned source after checking it is byte-identical to the pinned SHA-256.
     * Any difference (edit, re-encoding, line-ending change, newer upstream) stops the pipeline.
     */
    fun verifiedFile(sourcesDir: File, id: String): File {
        val pinned = source(id)
        val file = File(sourcesDir, pinned.path)
        if (!file.isFile) throw PipelineException("Missing source file ${file.path}")
        val actual = sha256Hex(file.readBytes())
        if (actual != pinned.sha256) {
            throw PipelineException("SHA-256 mismatch for '$id': pinned ${pinned.sha256}, actual $actual")
        }
        return file
    }

    /**
     * For a pinned FOLDER (path ending in "/"): checks the SHA-256 of all its files concatenated in
     * natural order (1.json, 2.json, ..., 132.json), and returns those files in that order.
     */
    fun verifiedDirectory(sourcesDir: File, id: String): List<File> {
        val pinned = source(id)
        ensure(pinned.path.endsWith("/")) { "Source '$id' is not a folder" }
        val dir = File(sourcesDir, pinned.path)
        val files = naturalOrder(dir)
        ensure(files.isNotEmpty()) { "Missing source folder ${dir.path}" }
        val actual = directorySha256(files)
        if (actual != pinned.sha256) {
            throw PipelineException("SHA-256 mismatch for '$id': pinned ${pinned.sha256}, actual $actual")
        }
        return files
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = false }

        fun load(sourcesDir: File): SourceLock =
            json.decodeFromString(serializer(), File(sourcesDir, "SOURCES.lock.json").readText())
    }
}

fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Throws [PipelineException] with [message] unless [condition] holds. */
internal inline fun ensure(condition: Boolean, message: () -> String) {
    if (!condition) throw PipelineException(message())
}

/** Files of [dir] sorted by the number in their name, then by name (1.json < 2.json < 10.json). */
fun naturalOrder(dir: File): List<File> =
    dir.listFiles { f -> f.isFile }.orEmpty()
        .sortedWith(compareBy<File>({ it.nameWithoutExtension.toIntOrNull() ?: Int.MAX_VALUE }, { it.name }))

fun directorySha256(files: List<File>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    files.forEach { digest.update(it.readBytes()) }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
