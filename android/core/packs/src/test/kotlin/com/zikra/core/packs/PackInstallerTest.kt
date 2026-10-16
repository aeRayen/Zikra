package com.zikra.core.packs

import android.database.sqlite.SQLiteDatabase
import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.network.NetworkPurpose
import com.zikra.core.network.Transport
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPOutputStream

/** WP 3.4b (CONTENT_PACKS_AR.md §3): every check refuses a bad pack and keeps the previous one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackInstallerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val keys = Ed25519KeyPairGenerator()
        .apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
        .generateKeyPair()
    private val private = keys.private as Ed25519PrivateKeyParameters

    /** X.509 SubjectPublicKeyInfo of an Ed25519 key: fixed 12-byte header + 32 raw bytes. */
    private val publicX509 = Base64.getEncoder().encodeToString(
        X509_ED25519_HEADER + (keys.public as Ed25519PublicKeyParameters).encoded,
    )

    private val requests = mutableListOf<String>()
    private val served = mutableMapOf<String, ByteArray>()
    private val transport = Transport { request ->
        requests += request.url
        assertEquals(NetworkPurpose.PublicContent, request.purpose)
        served[request.url]?.let { AppResult.Success(it) } ?: AppResult.Failure(AppError.Offline)
    }
    private val root by lazy { File(tmp.root, "packs") }
    private fun installer(appVersion: String = "1.0.0") = PackInstaller(root, transport, listOf(publicX509), appVersion)

    private val countCheck = PackContentCheck { db, v ->
        val n = db.rawQuery("SELECT COUNT(*) FROM item", null).use { c ->
            c.moveToFirst()
            c.getInt(0)
        }
        if (n != v.itemCount) throw PackRejected("count")
    }

    private fun database(items: Int): ByteArray {
        val f = File(tmp.root, "db-${System.nanoTime()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(f, null).use { db ->
            db.execSQL("CREATE TABLE item (n INTEGER)")
            repeat(items) { db.execSQL("INSERT INTO item VALUES ($it)") }
        }
        return f.readBytes()
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    /** Publishes a pack at the fake URLs; [tamper] can change the manifest before signing. */
    private fun publish(
        version: String,
        items: Int = 3,
        minApp: String = "0.1.0",
        tamper: (PackFile) -> PackFile = { it },
    ) {
        val db = database(items)
        val gz = gzip(db)
        val file = PackFile(
            "p.sqlite.gz",
            gz.size.toLong(),
            sha256Hex(gz),
            PackInstaller.GZIP,
            db.size.toLong(),
            sha256Hex(db),
        )
        val manifest = Manifest(
            packId = "id-$version", area = "hadith", language = "ar", editionCode = "ar.test", version = version,
            minAppVersion = minApp, createdAt = "now", source = PackSource("s", "l", "u", "v", "x"),
            files = listOf(tamper(file)), verification = PackVerification(itemCount = items),
        )
        val bytes = Json.encodeToString(Manifest.serializer(), manifest).toByteArray()
        val signature = Ed25519Signer().run {
            init(true, private)
            update(bytes, 0, bytes.size)
            generateSignature()
        }
        val signed = SignedManifest(
            Base64.getEncoder().encodeToString(bytes),
            Base64.getEncoder().encodeToString(signature),
            PackSignatures.keyId(publicX509),
        )
        served[MANIFEST] = Json.encodeToString(SignedManifest.serializer(), signed).toByteArray()
        served["$BASE/p.sqlite.gz"] = gz
    }

    private val urls = PackSourceUrls(MANIFEST, BASE)

    @Test
    fun aSoundPackIsInstalledAndRecorded() = runTest {
        publish("1")
        val result = installer().install(urls, countCheck)
        val pack = (result as AppResult.Success).value
        assertEquals("1", pack.version)
        assertEquals(pack, installer().installed("ar.test"))
        assertTrue(requireNotNull(installer().file("ar.test")).isFile)
        // The same version again: nothing is downloaded but the manifest.
        requests.clear()
        assertTrue(installer().install(urls, countCheck) is AppResult.Success)
        assertEquals(listOf(MANIFEST), requests)
    }

    @Test
    fun aNewVersionReplacesTheOldOneWhichIsDeleted() = runTest {
        publish("1")
        installer().install(urls, countCheck)
        val old = requireNotNull(installer().file("ar.test"))
        publish("2", items = 5)
        installer().install(urls, countCheck)
        assertEquals("2", installer().installed("ar.test")?.version)
        assertFalse(old.exists())
    }

    private suspend fun assertRefusedKeepingVersion1(
        error: AppError,
        publishBad: () -> Unit,
        appVersion: String = "1.0.0",
    ) {
        publish("1")
        installer().install(urls, countCheck)
        publishBad()
        assertEquals(AppResult.Failure(error), installer(appVersion).install(urls, countCheck))
        assertEquals("1", installer().installed("ar.test")?.version)
        assertTrue(requireNotNull(installer().file("ar.test")).isFile)
        assertTrue(root.listFiles().orEmpty().none { it.name.endsWith(".download") })
    }

    @Test
    fun aBadSignatureIsRefused() = runTest {
        assertRefusedKeepingVersion1(AppError.ContentIntegrity, {
            publish("2")
            val signed = Json.decodeFromString(SignedManifest.serializer(), served.getValue(MANIFEST).decodeToString())
            val other = Base64.getEncoder().encodeToString(ByteArray(64))
            val tampered = signed.copy(signature = other)
            served[MANIFEST] = Json.encodeToString(SignedManifest.serializer(), tampered).toByteArray()
        })
    }

    @Test
    fun aChangedDownloadIsRefused() = runTest {
        assertRefusedKeepingVersion1(AppError.ContentIntegrity, {
            publish("2")
            val bytes = served.getValue("$BASE/p.sqlite.gz").copyOf()
            bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 1).toByte()
            served["$BASE/p.sqlite.gz"] = bytes
        })
    }

    @Test
    fun aDifferentUnpackedFileIsRefused() = runTest {
        assertRefusedKeepingVersion1(AppError.ContentIntegrity, {
            publish("2") { it.copy(uncompressedSha256 = "0".repeat(64)) }
        })
    }

    @Test
    fun theAreasOwnCheckCanRefuse() = runTest {
        // The manifest says 3 items but the database has 4: the content check refuses it.
        assertRefusedKeepingVersion1(AppError.ContentIntegrity, {
            publish("2", items = 4)
            val signedText = served.getValue(MANIFEST).decodeToString()
            val signed = Json.decodeFromString(SignedManifest.serializer(), signedText)
            val text = Base64.getDecoder().decode(signed.manifest).decodeToString()
            val manifest = Json.decodeFromString(Manifest.serializer(), text)
            val changed = manifest.copy(verification = PackVerification(itemCount = 3))
            val bytes = Json.encodeToString(Manifest.serializer(), changed)
                .toByteArray()
            val signature = Ed25519Signer().run {
                init(true, private)
                update(bytes, 0, bytes.size)
                generateSignature()
            }
            served[MANIFEST] = Json.encodeToString(
                SignedManifest.serializer(),
                signed.copy(
                    manifest = Base64.getEncoder().encodeToString(bytes),
                    signature = Base64.getEncoder().encodeToString(signature),
                ),
            ).toByteArray()
        })
    }

    @Test
    fun aPackForANewerAppIsRefused() = runTest {
        assertRefusedKeepingVersion1(AppError.ContentIntegrity, { publish("2", minApp = "9.0.0") })
    }

    @Test
    fun offlineLeavesNothingBehind() = runTest {
        assertEquals(AppResult.Failure(AppError.Offline), installer().install(urls, countCheck))
        assertNull(installer().installed("ar.test"))
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(PackSignatures.compareVersions("0.10.0", "0.9.1") > 0)
        assertEquals(0, PackSignatures.compareVersions("1.0", "1.0.0-debug"))
        assertTrue(PackSignatures.compareVersions("0.1.0", "1.0.0") < 0)
    }

    @Test
    fun thePublishedHadithManifestOpensWithTheAppsKey() {
        val text = requireNotNull(javaClass.classLoader).getResource("hadith-ibnmajah.manifest.json").readText()
        assertEquals("95fdefc5b8026097", PackSignatures.keyId(PackSignatures.ZIKRA_CONTENT_KEY))
        val manifest = PackSignatures.open(text, listOf(PackSignatures.ZIKRA_CONTENT_KEY), "1.0.0")
        assertEquals("ar.ibnmajah.hadith-api", manifest.editionCode)
        assertEquals(4338, manifest.verification.itemCount)
        assertEquals("hadith-ibnmajah.sqlite.gz", manifest.files.single().path)
    }

    private companion object {
        const val BASE = "https://example.invalid/release"
        const val MANIFEST = "$BASE/m.json"
        val X509_ED25519_HEADER = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    }
}
