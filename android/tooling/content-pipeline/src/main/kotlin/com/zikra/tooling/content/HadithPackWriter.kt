package com.zikra.tooling.content

import com.zikra.core.i18n.SearchNormalizer
import java.io.File
import java.sql.DriverManager

/** Writes one book as the hadith pack database (WP 3.4a): text, normalized search column, gradings. */
object HadithPackWriter {
    fun write(out: File, data: HadithBookData, upstream: UpstreamFile) {
        if (out.exists() && !out.delete()) throw PipelineException("Cannot replace ${out.path}")
        DriverManager.getConnection("jdbc:sqlite:${out.absolutePath}").use { db ->
            db.autoCommit = false
            db.createStatement().use { st ->
                listOf(
                    "CREATE TABLE pack_info (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)",
                    "CREATE TABLE hadith_section (id INTEGER PRIMARY KEY NOT NULL, title_en TEXT NOT NULL)",
                    """CREATE TABLE hadith (number TEXT PRIMARY KEY NOT NULL, number_value REAL NOT NULL,
                       section INTEGER NOT NULL, text TEXT NOT NULL, search_text TEXT NOT NULL)""",
                    "CREATE INDEX hadith_order ON hadith (section, number_value)",
                    """CREATE TABLE hadith_grade (number TEXT NOT NULL, ord INTEGER NOT NULL, scholar TEXT NOT NULL,
                       grade TEXT NOT NULL, PRIMARY KEY (number, ord))""",
                ).forEach(st::execute)
            }
            fun insert(sql: String, rows: List<List<Any>>) = db.prepareStatement(sql).use { ps ->
                rows.forEach { row ->
                    row.forEachIndexed { i, v -> ps.setObject(i + 1, v) }
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            insert(
                "INSERT INTO pack_info VALUES (?,?)",
                listOf(
                    listOf("book", data.book.key),
                    listOf("name", data.name),
                    listOf("source_url", upstream.url),
                    listOf("source_sha256", upstream.sha256),
                    listOf("license", HadithPacks.LICENSE),
                ),
            )
            insert("INSERT INTO hadith_section VALUES (?,?)", data.sections.map { (id, t) -> listOf(id, t) })
            insert(
                "INSERT INTO hadith VALUES (?,?,?,?,?)",
                data.hadiths.map { h ->
                    listOf(h.number, h.numberValue, h.section, h.text, SearchNormalizer.normalize(h.text))
                },
            )
            insert(
                "INSERT INTO hadith_grade VALUES (?,?,?,?)",
                data.hadiths.flatMap { h -> h.grades.mapIndexed { i, (s, g) -> listOf(h.number, i, s, g) } },
            )
            db.commit()
            db.autoCommit = true
            db.createStatement().use { it.execute("VACUUM") }
        }
    }
}
