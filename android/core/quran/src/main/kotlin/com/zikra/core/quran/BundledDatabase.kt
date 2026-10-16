package com.zikra.core.quran

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * Opens a SQLite asset bundled in the APK, copying it out once per install/update (SQLite cannot read
 * compressed assets). Kept in noBackupFilesDir: app data restored from the APK, never backed up.
 * Shared by every bundled content database (Quran, translation, adhkar).
 */
object BundledDatabase {
    fun open(context: Context, asset: String, folder: String): Pair<SQLiteDatabase, File> {
        val app = context.applicationContext
        val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
        val dir = File(app.noBackupFilesDir, folder).apply { mkdirs() }
        val target = File(dir, "${asset.substringBeforeLast('.')}-$stamp.sqlite")
        if (!target.isFile) {
            val tmp = File(dir, "${target.name}.tmp")
            app.assets.open(asset).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            check(tmp.renameTo(target)) { "Cannot install $asset" }
            dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
        }
        val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
        return SQLiteDatabase.openDatabase(target.path, null, flags) to target
    }
}
