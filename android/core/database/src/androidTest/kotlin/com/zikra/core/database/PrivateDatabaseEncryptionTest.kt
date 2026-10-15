package com.zikra.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zikra.core.database.secure.KeystoreDatabaseKeyProvider
import com.zikra.core.database.secure.PrivateDatabase
import com.zikra.core.database.secure.PrivateMetaEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Acceptance for WP 1.7 (BUILD_PLAN): private.db is unreadable without its key.
 * Runs on a real device/emulator (SQLCipher native code and Android Keystore are not on the JVM).
 */
@RunWith(AndroidJUnit4::class)
class PrivateDatabaseEncryptionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile: File get() = context.getDatabasePath(PrivateDatabase.FILE_NAME)
    private val testAlias = "zikra_test_key"

    @Before
    fun clean() {
        context.deleteDatabase(PrivateDatabase.FILE_NAME)
        File(context.noBackupFilesDir, "$testAlias.wrapped").delete()
    }

    @After
    fun tearDown() = clean()

    private fun writeSecret(): ByteArray {
        val provider = KeystoreDatabaseKeyProvider(context, testAlias)
        val db = ZikraDatabases.secure(context, provider)
        runBlocking { db.metaDao().put(PrivateMetaEntity("probe", "secret-value")) }
        db.close()
        return provider.databaseKey()
    }

    @Test
    fun fileOnDiskIsNotPlainSqlite() {
        writeSecret()
        val header = dbFile.inputStream().use { it.readNBytes(16) }
        assertFalse("private.db must not start with the SQLite header", String(header).startsWith("SQLite format 3"))
        assertFalse("plaintext value found on disk", dbFile.readBytes().decodeToString().contains("secret-value"))
    }

    @Test
    fun plainSqliteCannotOpenIt() {
        writeSecret()
        try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT * FROM private_meta", null).use { it.count }
            }
            fail("plain SQLite read an encrypted database")
        } catch (expected: Exception) {
            // "file is not a database"
        }
    }

    @Test
    fun wrongKeyCannotOpenIt() {
        writeSecret()
        System.loadLibrary("sqlcipher")
        val wrong = Room.databaseBuilder(context, PrivateDatabase::class.java, PrivateDatabase.FILE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(ByteArray(32) { 7 }))
            .build()
        try {
            runBlocking { wrong.metaDao().get("probe") }
            fail("opened with a wrong key")
        } catch (expected: Exception) {
            // SQLiteException: file is not a database
        } finally {
            wrong.close()
        }
    }

    @Test
    fun rightKeyReadsItBackAndKeyIsStable() {
        val first = writeSecret()
        val provider = KeystoreDatabaseKeyProvider(context, testAlias)
        assertArrayEquals("key must survive process restarts", first, provider.databaseKey())
        assertEquals(32, first.size)

        val db = ZikraDatabases.secure(context, provider)
        assertEquals("secret-value", runBlocking { db.metaDao().get("probe") })
        db.close()
    }

    @Test
    fun onlyTheWrappedKeyIsStoredAndOutsideBackup() {
        val key = writeSecret()
        val wrapped = File(context.noBackupFilesDir, "$testAlias.wrapped")
        assertTrue(wrapped.exists())
        assertFalse("raw key must not be stored", wrapped.readBytes().toList().windowed(key.size).contains(key.toList()))
    }
}
