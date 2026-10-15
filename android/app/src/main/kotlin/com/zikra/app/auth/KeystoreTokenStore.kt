package com.zikra.app.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.zikra.core.network.StoredSession
import com.zikra.core.network.TokenStore
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Session tokens encrypted with an AES-256-GCM key held in the Android Keystore (non-exportable),
 * stored in noBackupFilesDir: never in backups or device transfer (SECURITY_AND_PRIVACY_REVIEW T5).
 * If the key is lost (reinstall, Keystore reset) the session is simply gone and the user is a guest.
 */
class KeystoreTokenStore(context: Context, private val alias: String = DEFAULT_ALIAS) : TokenStore {

    private val file = File(context.noBackupFilesDir, "$alias.bin")
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    override fun load(): StoredSession? {
        if (!file.isFile) return null
        return runCatching {
            val blob = file.readBytes()
            val ivLength = blob[0].toInt()
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, blob.copyOfRange(1, 1 + ivLength)))
            }
            val plain = cipher.doFinal(blob, 1 + ivLength, blob.size - 1 - ivLength)
            json.decodeFromString(StoredSession.serializer(), plain.decodeToString())
        }.getOrElse {
            // Unreadable (key lost or file corrupted): drop it instead of crashing; user becomes a guest.
            file.delete()
            null
        }
    }

    @Synchronized
    override fun save(session: StoredSession) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.doFinal(json.encodeToString(StoredSession.serializer(), session).toByteArray())
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + sealed)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    @Synchronized
    override fun clear() {
        file.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(KEY_BITS)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val DEFAULT_ALIAS = "zikra_session"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
