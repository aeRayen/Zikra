package com.zikra.core.packs

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.network.NetworkPurpose
import com.zikra.core.network.NetworkRequest
import com.zikra.core.network.Transport
import kotlinx.serialization.Serializable
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/** Where one pack's two files are published (e.g. a GitHub release): the signed manifest and its file. */
data class PackSourceUrls(val manifestUrl: String, val fileBaseUrl: String)

/** The area's own check of an unpacked database (CONTENT_PACKS_AR.md §3.6): throws [PackRejected]. */
fun interface PackContentCheck {
    fun check(db: SQLiteDatabase, verification: PackVerification)
}

@Serializable
data class InstalledPack(val editionCode: String, val version: String, val packId: String, val fileName: String)

/**
 * Downloads, verifies and installs signed packs (CONTENT_PACKS_AR.md §3), refusing at the first failed
 * check: signature and app version, size and SHA-256 of the download, of the unpacked file, SQLite
 * integrity, the area's own check. Installation is atomic: the new file is moved into place, then
 * recorded, then the old version is deleted. Any failure deletes the temporary files and keeps the
 * previous version. Public content only: no user data is sent (NetworkPurpose.PublicContent).
 */
class PackInstaller(
    private val root: File,
    private val transport: Transport,
    private val trustedKeys: List<String> = listOf(PackSignatures.ZIKRA_CONTENT_KEY),
    private val appVersion: String,
) {
    private val registry = PackRegistry(root)

    fun installed(editionCode: String): InstalledPack? = registry.read()[editionCode]

    /** The installed database file of [editionCode], or null. */
    fun file(editionCode: String): File? = installed(editionCode)?.let { File(root, it.fileName) }?.takeIf { it.isFile }

    suspend fun install(urls: PackSourceUrls, check: PackContentCheck): AppResult<InstalledPack> = try {
        AppResult.Success(installOrThrow(urls, check))
    } catch (e: DownloadFailed) {
        AppResult.Failure(e.error)
    } catch (_: PackRejected) {
        AppResult.Failure(AppError.ContentIntegrity)
    } catch (_: IOException) {
        AppResult.Failure(AppError.Storage)
    }

    private suspend fun installOrThrow(urls: PackSourceUrls, check: PackContentCheck): InstalledPack {
        val manifest = PackSignatures.open(fetch(urls.manifestUrl).toString(Charsets.UTF_8), trustedKeys, appVersion)
        val current = installed(manifest.editionCode)?.takeIf { it.version == manifest.version }
        if (current != null) return current
        val entry = manifest.files.singleOrNull() ?: throw PackRejected("A pack has exactly one file")
        val download = fetch(urls.fileBaseUrl.trimEnd('/') + "/" + entry.path)
        return place(manifest, entry, download, check)
    }

    private fun place(
        manifest: Manifest,
        entry: PackFile,
        download: ByteArray,
        check: PackContentCheck,
    ): InstalledPack {
        root.mkdirs()
        val temp = File(root, "${manifest.editionCode}.download")
        try {
            requireMatch(download.size.toLong() == entry.sizeBytes && sha256Hex(download) == entry.sha256, "download")
            val bytes = if (entry.compression == GZIP) unpack(download, entry) else download
            temp.writeBytes(bytes)
            openChecked(temp, manifest.verification, check)
            val name = "${manifest.editionCode}-${manifest.version}.sqlite"
            if (!temp.renameTo(File(root, name))) throw IOException("Cannot move ${temp.name}")
            val previous = installed(manifest.editionCode)
            val pack = InstalledPack(manifest.editionCode, manifest.version, manifest.packId, name)
            registry.write(registry.read() + (manifest.editionCode to pack))
            previous?.takeIf { it.fileName != name }?.let { File(root, it.fileName).delete() }
            return pack
        } finally {
            temp.delete()
        }
    }

    /** A network failure (offline, not found...) as it is: the caller decides when to try again. */
    private class DownloadFailed(val error: AppError) : Exception()

    private suspend fun fetch(url: String): ByteArray =
        when (val r = transport.send(NetworkRequest(NetworkPurpose.PublicContent, "GET", url))) {
            is AppResult.Success -> r.value
            is AppResult.Failure -> throw DownloadFailed(r.error)
        }

    private fun unpack(gz: ByteArray, entry: PackFile): ByteArray {
        val bytes = GZIPInputStream(gz.inputStream()).use { it.readBytes() }
        val same = bytes.size.toLong() == entry.uncompressedSizeBytes && sha256Hex(bytes) == entry.uncompressedSha256
        requireMatch(same, "unpacked file")
        return bytes
    }

    private fun openChecked(file: File, verification: PackVerification, check: PackContentCheck) {
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val ok = db.rawQuery("PRAGMA integrity_check", null).use { c ->
                    c.moveToFirst() && c.getString(0) == "ok"
                }
                requireMatch(ok, "SQLite integrity")
                check.check(db, verification)
            }
        } catch (e: SQLiteException) {
            throw PackRejected("Not a readable database", e)
        }
    }

    private fun requireMatch(ok: Boolean, what: String) {
        if (!ok) throw PackRejected("$what does not match the manifest")
    }

    companion object {
        const val GZIP = "gzip"
    }
}
