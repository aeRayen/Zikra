package com.zikra.core.packs

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.util.Base64

// The manifest as written by the content pipeline (CONTENT_PACKS_AR.md §2); same field names.

@Serializable
data class PackFile(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    val compression: String? = null,
    val uncompressedSizeBytes: Long? = null,
    val uncompressedSha256: String? = null,
)

@Serializable
data class PackSource(
    val name: String,
    val license: String,
    val url: String,
    val version: String,
    val sourceSha256: String,
)

@Serializable
data class PackVerification(
    val surahCount: Int? = null,
    val ayahCount: Int? = null,
    val perSurahAyahCountsSha256: String? = null,
    val itemCount: Int? = null,
    val itemKeysSha256: String? = null,
)

@Serializable
data class Manifest(
    val schema: Int = 1,
    val packId: String,
    val area: String,
    val language: String?,
    val editionCode: String,
    val version: String,
    val minAppVersion: String,
    val createdAt: String,
    val source: PackSource,
    val files: List<PackFile>,
    val verification: PackVerification,
)

@Serializable
data class SignedManifest(val manifest: String, val signature: String, val keyId: String)

/** Why a pack was refused; the previous version (if any) stays installed. */
class PackRejected(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Opens a signed manifest (CONTENT_PACKS_AR.md §3, steps 1-3): the key is one the app trusts, the
 * Ed25519 signature over the exact manifest bytes is valid, and this app version is new enough.
 */
object PackSignatures {
    /** The content signing key (ADR-014), X.509 base64; keyId 95fdefc5b8026097. Public by design. */
    const val ZIKRA_CONTENT_KEY = "MCowBQYDK2VwAyEAdL001mdRfveKhZHuvHUPa+L39wY2xxhTIqBFCkXJBX4="

    private const val KEY_ID_HEX_CHARS = 16

    /** Ed25519 raw keys are the last 32 bytes of their X.509 encoding. */
    private const val RAW_KEY_BYTES = 32

    private val json = Json { ignoreUnknownKeys = true }

    fun keyId(x509Base64: String): String = sha256Hex(Base64.getDecoder().decode(x509Base64)).take(KEY_ID_HEX_CHARS)

    fun open(signedJson: String, trustedKeys: List<String>, appVersion: String): Manifest = try {
        val signed = json.decodeFromString(SignedManifest.serializer(), signedJson)
        val key = trustedKeys.firstOrNull { keyId(it) == signed.keyId }
        reject(key == null) { "Unknown key ${signed.keyId}" }
        val bytes = Base64.getDecoder().decode(signed.manifest)
        val valid = verify(requireNotNull(key), bytes, Base64.getDecoder().decode(signed.signature))
        reject(!valid) { "Invalid signature" }
        val manifest = json.decodeFromString(Manifest.serializer(), bytes.toString(Charsets.UTF_8))
        reject(compareVersions(manifest.minAppVersion, appVersion) > 0) {
            "Needs app ${manifest.minAppVersion}, this is $appVersion"
        }
        manifest
    } catch (e: IllegalArgumentException) {
        // Unreadable JSON or base64 (kotlinx SerializationException is an IllegalArgumentException).
        throw PackRejected("Unreadable manifest", e)
    }

    private inline fun reject(condition: Boolean, message: () -> String) {
        if (condition) throw PackRejected(message())
    }

    private fun verify(x509Base64: String, message: ByteArray, signature: ByteArray): Boolean {
        val encoded = Base64.getDecoder().decode(x509Base64)
        val raw = encoded.copyOfRange(encoded.size - RAW_KEY_BYTES, encoded.size)
        return Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(raw, 0))
            update(message, 0, message.size)
            verifySignature(signature)
        }
    }

    /** Numeric dotted versions: "0.10.0" > "0.9.1"; a missing part counts as 0, a suffix ("-debug") is ignored. */
    internal fun compareVersions(a: String, b: String): Int {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val (x, y) = parts(a) to parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }
}

internal fun sha256Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
