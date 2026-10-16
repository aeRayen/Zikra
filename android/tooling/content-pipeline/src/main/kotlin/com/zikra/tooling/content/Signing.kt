package com.zikra.tooling.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

@Serializable
data class PackFile(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
    /** "gzip" when [path] is compressed for download; the app checks the result against [uncompressedSha256]. */
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
/**
 * Structure the app checks after download (CONTENT_PACKS_AR.md §3.6). Quran packs: surahs, ayahs and the
 * per-surah counts; other areas (hadith, WP 3.4): the number of items and a hash of their keys.
 */
data class PackVerification(
    val surahCount: Int? = null,
    val ayahCount: Int? = null,
    val perSurahAyahCountsSha256: String? = null,
    val itemCount: Int? = null,
    val itemKeysSha256: String? = null,
)

/** CONTENT_PACKS_AR.md §2. */
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

/** What the app downloads: the exact signed manifest bytes (base64) + signature + key id. */
@Serializable
data class SignedManifest(val manifest: String, val signature: String, val keyId: String)

object Signing {
    private const val ALGORITHM = "Ed25519"
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }
    private val b64 = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    /** Creates a key pair. The private key file must stay outside the repository (CONTRIBUTING §4). */
    fun generateKeyPair(privateOut: File, publicOut: File): String {
        val pair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair()
        privateOut.writeText(b64.encodeToString(pair.private.encoded))
        publicOut.writeText(b64.encodeToString(pair.public.encoded))
        return keyId(pair.public)
    }

    fun loadPrivate(file: File): PrivateKey =
        KeyFactory.getInstance(ALGORITHM).generatePrivate(PKCS8EncodedKeySpec(b64d.decode(file.readText().trim())))

    fun loadPublic(file: File): PublicKey =
        KeyFactory.getInstance(ALGORITHM).generatePublic(X509EncodedKeySpec(b64d.decode(file.readText().trim())))

    /** First 8 bytes of SHA-256(public key), hex: lets the app pick the right key during rotation. */
    fun keyId(key: PublicKey): String = sha256Hex(key.encoded).take(KEY_ID_HEX_CHARS)

    fun sign(manifest: Manifest, privateKey: PrivateKey, publicKey: PublicKey): SignedManifest {
        val bytes = json.encodeToString(manifest).toByteArray(Charsets.UTF_8)
        val signature = Signature.getInstance(ALGORITHM).run {
            initSign(privateKey)
            update(bytes)
            sign()
        }
        return SignedManifest(b64.encodeToString(bytes), b64.encodeToString(signature), keyId(publicKey))
    }

    /** Returns the manifest only if the signature over its exact bytes is valid for [publicKey]. */
    fun open(signed: SignedManifest, publicKey: PublicKey): Manifest {
        if (signed.keyId != keyId(publicKey)) throw PipelineException("Unknown signing key ${signed.keyId}")
        val bytes = b64d.decode(signed.manifest)
        val valid = Signature.getInstance(ALGORITHM).run {
            initVerify(publicKey)
            update(bytes)
            verify(b64d.decode(signed.signature))
        }
        if (!valid) throw PipelineException("Invalid manifest signature")
        return Json.decodeFromString(Manifest.serializer(), bytes.toString(Charsets.UTF_8))
    }

    fun encode(signed: SignedManifest): String = json.encodeToString(signed)

    fun decode(text: String): SignedManifest = Json.decodeFromString(SignedManifest.serializer(), text)

    private const val KEY_ID_HEX_CHARS = 16
}
