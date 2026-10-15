package com.zikra.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zikra.app.auth.KeystoreTokenStore
import com.zikra.core.network.StoredSession
import com.zikra.core.network.SyncConsent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** WP 1.12 on a real Android runtime: session tokens are encrypted at rest with a Keystore key. */
@RunWith(AndroidJUnit4::class)
class KeystoreTokenStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val alias = "zikra_session_test"
    private val store = KeystoreTokenStore(context, alias)
    private val session = StoredSession(
        userId = "u1",
        email = "me@example.com",
        accessToken = "access-token-plaintext-marker",
        refreshToken = "refresh-token-plaintext-marker",
        expiresAtEpochSeconds = 1_900_000_000,
        grantedConsents = setOf(SyncConsent.BOOKMARKS),
    )

    @After
    fun tearDown() = store.clear()

    @Test
    fun roundTripsTheSession() {
        store.save(session)
        assertEquals(session, KeystoreTokenStore(context, alias).load())
    }

    @Test
    fun nothingReadableIsWrittenToDisk() {
        store.save(session)
        val bytes = File(context.noBackupFilesDir, "$alias.bin").readBytes().decodeToString()
        assertFalse(bytes.contains("plaintext-marker"))
        assertFalse(bytes.contains("me@example.com"))
    }

    @Test
    fun corruptedFileIsDroppedNotCrashing() {
        File(context.noBackupFilesDir, "$alias.bin").writeBytes(byteArrayOf(12, 1, 2, 3))
        assertNull(store.load())
    }

    @Test
    fun clearSignsOutOnDevice() {
        store.save(session)
        store.clear()
        assertNull(store.load())
    }
}
