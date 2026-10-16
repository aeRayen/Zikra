package com.zikra.core.quran

import com.zikra.core.i18n.SearchNormalizer

/**
 * Ayahs whose search text contains [query] (normalized like the index: no tashkeel, unified hamza and
 * ta marbuta, modern spelling via the simple text), in mushaf order. Fewer than 2 letters: nothing.
 */
fun QuranText.search(query: String, limit: Int = QuranText.SEARCH_LIMIT): QuranSearchResult {
    val key = SearchNormalizer.normalize(query)
    if (key.length < MIN_QUERY_LENGTH) return QuranSearchResult(emptyList(), 0)
    // The user's text is a LIKE pattern only through escaping: % and _ match themselves.
    val escaped = key.replace("!", "!!").replace("%", "!%").replace("_", "!_")
    val pattern = "%$escaped%"
    val where = "t.edition = '${QuranText.EDITION}' AND t.search_text LIKE ? ESCAPE '!'"
    val total = db.rawQuery("SELECT COUNT(*) FROM quran_ayah_text t WHERE $where", arrayOf(pattern)).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }
    val sql = """
        SELECT t.surah, t.ayah, t.text, m.juz, m.hizb_quarter, m.page, m.sajda
        FROM quran_ayah_text t JOIN quran_ayah_meta m ON m.surah = t.surah AND m.ayah = t.ayah
        WHERE $where ORDER BY t.surah, t.ayah LIMIT $limit
    """
    val ayahs = db.rawQuery(sql, arrayOf(pattern)).use { c -> c.ayahs() }
    return QuranSearchResult(ayahs, total)
}

private const val MIN_QUERY_LENGTH = 2
