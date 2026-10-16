package com.zikra.core.hadith

import android.database.sqlite.SQLiteDatabase
import com.zikra.core.packs.PackContentCheck
import com.zikra.core.packs.PackRejected
import com.zikra.core.packs.PackSourceUrls
import java.security.MessageDigest

/** The six books, in their customary order (WP 3.4, ADR-046). [key] matches the pack names. */
enum class HadithBook(val key: String) {
    BUKHARI("bukhari"),
    MUSLIM("muslim"),
    ABU_DAWUD("abudawud"),
    TIRMIDHI("tirmidhi"),
    NASAI("nasai"),
    IBN_MAJAH("ibnmajah"),
    ;

    val editionCode: String get() = "ar.$key.hadith-api"
}

/** Where the signed packs are published: release hadith-packs-2026.10.16 on GitHub (ADR-046). */
object HadithCatalog {
    const val RELEASE = "https://github.com/aeRayen/Zikra/releases/download/hadith-packs-2026.10.16"

    fun urls(book: HadithBook) = PackSourceUrls("$RELEASE/hadith-${book.key}.manifest.json", RELEASE)
}

/**
 * A hadith pack holds exactly the hadiths its manifest names, in order: their count and the SHA-256
 * of their numbers (one per line) - computed the same way by the content pipeline (HadithPacks).
 */
object HadithPackCheck : PackContentCheck {
    override fun check(db: SQLiteDatabase, verification: com.zikra.core.packs.PackVerification) {
        val numbers = db.rawQuery("SELECT number FROM hadith ORDER BY rowid", null).use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        if (numbers.size != verification.itemCount) throw PackRejected("Expected ${verification.itemCount} hadiths")
        if (sha256Hex(numbers.joinToString("\n").toByteArray(Charsets.UTF_8)) != verification.itemKeysSha256) {
            throw PackRejected("Hadith numbers differ from the manifest")
        }
    }

    private fun sha256Hex(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
