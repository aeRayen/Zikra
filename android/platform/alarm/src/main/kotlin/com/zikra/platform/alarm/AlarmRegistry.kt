package com.zikra.platform.alarm

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** What an alarm is for; decides the AlarmManager API and which handler runs when it fires. */
@Serializable
enum class AlarmKind {
    /** Prayer-time adhan/notification (WP 2.5): exact, allowed while idle, no status-bar alarm icon. */
    ADHAN,

    /** A wake-up alarm the user set (Phase 5): shown as the next alarm clock by the system. */
    USER_ALARM,
}

/** One scheduled alarm. [key] is stable per purpose (e.g. "adhan:2026-06-01:FAJR"); [requestCode] is unique. */
@Serializable
data class AlarmEntry(
    val key: String,
    val kind: AlarmKind,
    val triggerAtEpochMs: Long,
    val requestCode: Int,
)

@Serializable
internal data class RegistryFile(
    val version: Int = 1,
    val nextRequestCode: Int = FIRST_REQUEST_CODE,
    val entries: List<AlarmEntry> = emptyList()
)

internal const val FIRST_REQUEST_CODE = 1_000

/**
 * The alarms we have handed to AlarmManager, so they can be re-armed after a reboot (the system drops
 * them) and cancelled exactly. Kept in DEVICE-PROTECTED storage so it is readable before the first unlock
 * after a reboot (LOCAL_SCHEMA_AR.md, direct boot). It holds only times and keys - never a location.
 *
 * Not thread-safe by itself: [AlarmScheduler] serializes access.
 */
class AlarmRegistry(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true }

    fun entries(): List<AlarmEntry> = read().entries

    /** Replaces all entries of [kind] with [keysAndTimes]; keeps request codes of unchanged keys. */
    internal fun replace(kind: AlarmKind, keysAndTimes: List<Pair<String, Long>>): Replacement {
        val current = read()
        val (sameKind, others) = current.entries.partition { it.kind == kind }
        val codes = sameKind.associate { it.key to it.requestCode }
        var next = current.nextRequestCode
        val added = keysAndTimes.distinctBy { it.first }.map { (key, at) ->
            AlarmEntry(key, kind, at, codes[key] ?: next++)
        }
        write(RegistryFile(nextRequestCode = next, entries = others + added))
        val keptCodes = added.map { it.requestCode }.toSet()
        return Replacement(added = added, removed = sameKind.filter { it.requestCode !in keptCodes })
    }

    internal fun remove(requestCode: Int) {
        val current = read()
        if (current.entries.none { it.requestCode == requestCode }) return
        write(current.copy(entries = current.entries.filter { it.requestCode != requestCode }))
    }

    /** Drops entries whose time has passed; returns what remains. */
    internal fun pruneBefore(epochMs: Long): List<AlarmEntry> {
        val current = read()
        val (future, past) = current.entries.partition { it.triggerAtEpochMs > epochMs }
        if (past.isNotEmpty()) write(current.copy(entries = future))
        return future
    }

    internal data class Replacement(val added: List<AlarmEntry>, val removed: List<AlarmEntry>)

    private fun read(): RegistryFile = try {
        if (file.isFile) json.decodeFromString(RegistryFile.serializer(), file.readText()) else RegistryFile()
    } catch (_: SerializationException) {
        RegistryFile() // damaged: start over; producers re-plan on the next refresh
    } catch (_: IllegalArgumentException) {
        RegistryFile()
    }

    /** Write-then-move so a crash mid-write never leaves a half file. */
    private fun write(content: RegistryFile) {
        file.parentFile?.let { if (!it.isDirectory && !it.mkdirs()) throw IOException("Cannot create ${it.path}") }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(json.encodeToString(RegistryFile.serializer(), content))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
