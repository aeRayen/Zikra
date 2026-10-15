package com.zikra.core.database.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Supplies the 256-bit SQLCipher key for private.db. Callers must wipe the returned array after use. */
fun interface DatabaseKeyProvider {
    fun databaseKey(): ByteArray
}

/**
 * Generates a random 256-bit database key once, wraps it with an AES-256-GCM key held in the
 * Android Keystore (non-exportable) and stores only the wrapped bytes in noBackupFilesDir, which
 * Android never backs up or transfers (ADR-006, SECURITY_AND_PRIVACY_REVIEW T2).
 *
 * Consequence (by design): after a reinstall, a restore to a new phone, or a Keystore reset, the
 * key is gone and private.db cannot be opened; it is then recreated empty.
 */
class KeystoreDatabaseKeyProvider(
    context: Context,
    private val alias: String = DEFAULT_ALIAS,
) : DatabaseKeyProvider {

    private val wrappedKeyFile = File(context.noBackupFilesDir, "$alias.wrapped")

    @Synchronized
    override fun databaseKey(): ByteArray {
        val wrapping = wrappingKey()
        if (wrappedKeyFile.exists()) {
            return unwrap(wrappedKeyFile.readBytes(), wrapping)
        }
        val key = ByteArray(KEY_BYTES).also { SecureRandom().nextBytes(it) }
        wrappedKeyFile.writeBytes(wrap(key, wrapping))
        return key
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BYTES * Byte.SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    /** Layout: [iv length (1 byte)][iv][ciphertext+tag]. */
    private fun wrap(plain: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        return byteArrayOf(iv.size.toByte()) + iv + cipher.doFinal(plain)
    }

    private fun unwrap(blob: ByteArray, key: SecretKey): ByteArray {
        val ivLength = blob[0].toInt()
        val iv = blob.copyOfRange(1, 1 + ivLength)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(blob, 1 + ivLength, blob.size - 1 - ivLength)
    }

    private companion object {
        const val DEFAULT_ALIAS = "zikra_private_db_key"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val GCM_TAG_BITS = 128
    }
}
