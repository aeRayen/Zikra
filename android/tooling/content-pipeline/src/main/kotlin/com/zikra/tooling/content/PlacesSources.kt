package com.zikra.tooling.content

import java.io.File
import java.util.zip.ZipFile

/*
 * GeoNames gazetteer (CC BY 4.0, https://www.geonames.org) for the manual city picker (WP 2.1).
 * Columns follow https://download.geonames.org/export/dump/readme.txt.
 */

/** Languages whose city names are bundled: the three language waves of BUILD_PLAN §2.3. */
val PLACE_NAME_LANGUAGES = listOf(
    "ar", "en", "ur", "id", "tr", "fr", "bn", "ms", "fa", "ru", "es", "de", "ha", "sw", "so", "ps", "uz", "zh",
)

data class GeoCity(
    val id: Long,
    val name: String,
    val asciiName: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    val admin1: String,
    val population: Long,
    val timezone: String,
)

data class GeoCountry(val code: String, val name: String, val geonameId: Long)

/** [code] is "<country>.<admin1>", e.g. "EG.11". */
data class GeoAdmin1(val code: String, val name: String, val geonameId: Long)

data class GeoName(
    val geonameId: Long,
    val language: String,
    val name: String,
    val preferred: Boolean,
    val short: Boolean,
)

private const val COL_ID = 0
private const val COL_NAME = 1
private const val COL_ASCII = 2
private const val COL_LAT = 4
private const val COL_LON = 5
private const val COL_COUNTRY = 8
private const val COL_ADMIN1 = 10
private const val COL_POPULATION = 14
private const val COL_TIMEZONE = 17
private const val CITY_COLUMNS = 19

private const val COUNTRY_ISO = 0
private const val COUNTRY_NAME = 4
private const val COUNTRY_GEONAME_ID = 16

private fun String.columns() = split('\t')

/** Reads cities15000.txt from the pinned upstream zip. */
fun parseGeoCities(zip: File): List<GeoCity> = ZipFile(zip).use { z ->
    val entry = z.getEntry("cities15000.txt") ?: throw PipelineException("cities15000.txt missing in ${zip.name}")
    z.getInputStream(entry).bufferedReader(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.map { line ->
        val c = line.columns()
        ensure(c.size == CITY_COLUMNS) { "Unexpected city row: ${c.firstOrNull()}" }
        GeoCity(
            id = c[COL_ID].toLong(),
            name = c[COL_NAME],
            asciiName = c[COL_ASCII],
            latitude = c[COL_LAT].toDouble(),
            longitude = c[COL_LON].toDouble(),
            country = c[COL_COUNTRY],
            admin1 = c[COL_ADMIN1],
            population = c[COL_POPULATION].toLongOrNull() ?: 0,
            timezone = c[COL_TIMEZONE],
        )
    }.toList()
}

fun parseGeoCountries(file: File): List<GeoCountry> = file.readLines(Charsets.UTF_8)
    .filter { it.isNotBlank() && !it.startsWith("#") }
    .map { line ->
        val c = line.columns()
        GeoCountry(c[COUNTRY_ISO], c[COUNTRY_NAME], c[COUNTRY_GEONAME_ID].toLong())
    }

fun parseGeoAdmin1(file: File): List<GeoAdmin1> = file.readLines(Charsets.UTF_8)
    .filter { it.isNotBlank() }
    .map { line ->
        val c = line.columns()
        GeoAdmin1(code = c[0], name = c[1], geonameId = c[3].toLong())
    }

/** Reads the derived names file written by [PlacesExtract]: id, language, name, preferred, short. */
fun parseGeoNames(file: File): List<GeoName> = file.readLines(Charsets.UTF_8)
    .filter { it.isNotBlank() }
    .map { line ->
        val c = line.columns()
        GeoName(c[0].toLong(), c[1], c[2], preferred = c[3] == "1", short = c[4] == "1")
    }

/**
 * Derives the pinned names file from GeoNames alternateNamesV2.txt (~800 MB, not stored in git).
 * Keeps only names of the bundled cities, countries and regions, in [PLACE_NAME_LANGUAGES], and drops
 * colloquial, historic and ended names. Output is sorted, so the same input gives the same bytes.
 */
object PlacesExtract {
    private const val ALT_GEONAME_ID = 1
    private const val ALT_LANGUAGE = 2
    private const val ALT_NAME = 3
    private const val ALT_PREFERRED = 4
    private const val ALT_SHORT = 5
    private const val ALT_COLLOQUIAL = 6
    private const val ALT_HISTORIC = 7
    private const val ALT_TO = 9

    fun run(alternateNames: File, wantedIds: Set<Long>, out: File) {
        val languages = PLACE_NAME_LANGUAGES.toSet()
        val rows = sortedSetOf<String>(compareBy<String>({ it.substringBefore('\t').toLong() }, { it }))
        alternateNames.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val c = line.columns()
                val id = c.getOrNull(ALT_GEONAME_ID)?.toLongOrNull() ?: return@forEach
                if (id !in wantedIds || c[ALT_LANGUAGE] !in languages) return@forEach
                val excluded = c[ALT_COLLOQUIAL] == "1" || c[ALT_HISTORIC] == "1"
                // A "from" year alone marks a current name (e.g. Mumbai since 1995); a "to" year means it ended.
                val ended = c.getOrElse(ALT_TO) { "" }.isNotEmpty()
                if (excluded || ended || c[ALT_NAME].isBlank()) return@forEach
                val flag = { i: Int -> if (c[i] == "1") "1" else "0" }
                val row = listOf(id, c[ALT_LANGUAGE], c[ALT_NAME], flag(ALT_PREFERRED), flag(ALT_SHORT))
                rows += row.joinToString("\t")
            }
        }
        out.parentFile?.mkdirs()
        out.writeText(rows.joinToString(separator = "\n", postfix = "\n"), Charsets.UTF_8)
    }
}
