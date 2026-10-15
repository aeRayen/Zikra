package com.zikra.core.database

import android.content.Context
import androidx.room.Room
import com.zikra.core.database.content.ContentDatabase
import com.zikra.core.database.secure.DatabaseKeyProvider
import com.zikra.core.database.secure.PrivateDatabase
import com.zikra.core.database.user.UserDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The only place that opens the app databases (ADR-006). No destructive migration fallback:
 * a missing migration must fail loudly in development, never wipe user data.
 */
object ZikraDatabases {

    fun user(context: Context): UserDatabase = Room
        .databaseBuilder(context.applicationContext, UserDatabase::class.java, UserDatabase.FILE_NAME)
        .build()

    fun content(context: Context): ContentDatabase = Room
        .databaseBuilder(context.applicationContext, ContentDatabase::class.java, ContentDatabase.FILE_NAME)
        .build()

    /** private.db, encrypted with SQLCipher using the device-bound key from [keyProvider]. */
    fun secure(context: Context, keyProvider: DatabaseKeyProvider): PrivateDatabase {
        System.loadLibrary(SQLCIPHER_LIBRARY)
        return Room
            .databaseBuilder(context.applicationContext, PrivateDatabase::class.java, PrivateDatabase.FILE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(keyProvider.databaseKey()))
            .build()
    }

    private const val SQLCIPHER_LIBRARY = "sqlcipher"
}
