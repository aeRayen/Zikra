package com.zikra.core.places

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zikra.core.i18n.SearchNormalizer
import java.io.Closeable
import java.io.File
import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** A city, with names in the requested language (falling back to English, then the GeoNames name). */
data class Place(
    val cityId: Long,
    val name: String,
    val region: String?,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: ZoneId,
)

data class NearestPlace(val place: Place, val distanceKm: Double)

/**
 * Read-only access to the bundled GeoNames city database (WP 2.1). Works fully offline, so a user who
 * refuses the location permission can still pick a city. All calls do disk I/O: call off the main thread.
 */
class PlaceDirectory private constructor(private val db: SQLiteDatabase) : Closeable {

    /**
     * Cities whose name in ANY bundled language starts with [query] (or has a word starting with it),
     * exact matches first, then by population.
     */
    fun search(query: String, language: String, limit: Int = DEFAULT_LIMIT): List<Place> {
        val key = SearchNormalizer.normalize(query)
        if (key.isEmpty()) return emptyList()
        val lang = baseLanguage(language)
        val sql = """
            SELECT $COLUMNS FROM (
              SELECT city_id AS id, MAX(key = ?) AS exact FROM city_search
              WHERE key >= ? AND key < ? GROUP BY city_id
            ) m JOIN city c ON c.id = m.id $JOINS
            ORDER BY m.exact DESC, c.population DESC LIMIT ${limit.coerceIn(1, MAX_LIMIT)}
        """
        return db.rawQuery(sql, arrayOf(key, key, key + MAX_CHAR, lang, lang, lang)).use { it.places() }
    }

    fun byId(cityId: Long, language: String): Place? {
        val lang = baseLanguage(language)
        val sql = "SELECT $COLUMNS FROM city c $JOINS WHERE c.id = ?"
        return db.rawQuery(sql, arrayOf(lang, lang, lang, cityId.toString())).use { it.places().firstOrNull() }
    }

    /** The closest bundled city to a (coarse) device location, or null in the middle of nowhere. */
    fun nearest(latitude: Double, longitude: Double, language: String): NearestPlace? {
        // Only trust a hit closer than the band edge; otherwise a wider band may hold a closer city.
        val (id, km) = SEARCH_WINDOWS_DEG.firstNotNullOfOrNull { window ->
            closestInBand(latitude, longitude, window)?.takeIf { it.second <= window * KM_PER_DEGREE }
        } ?: return null
        return byId(id, language)?.let { NearestPlace(it, km) }
    }

    override fun close() = db.close()

    /** Closest city whose latitude is within [window] degrees, as (id, km). */
    private fun closestInBand(latitude: Double, longitude: Double, window: Double): Pair<Long, Double>? {
        val sql = "SELECT id, latitude, longitude FROM city WHERE latitude BETWEEN ? AND ?"
        val args = arrayOf((latitude - window).toString(), (latitude + window).toString())
        return db.rawQuery(sql, args).use { c ->
            val candidates = generateSequence { c.takeIf { it.moveToNext() } }.map {
                it.getLong(0) to distanceKm(latitude, longitude, it.getDouble(1), it.getDouble(2))
            }
            candidates.minByOrNull { it.second }
        }
    }

    private fun Cursor.places(): List<Place> = buildList {
        while (moveToNext()) {
            add(
                Place(
                    cityId = getLong(COL_ID),
                    latitude = getDouble(COL_LAT),
                    longitude = getDouble(COL_LON),
                    timeZone = ZoneId.of(getString(COL_ZONE)),
                    countryCode = getString(COL_COUNTRY_CODE),
                    name = getString(COL_NAME),
                    country = getString(COL_COUNTRY),
                    region = if (isNull(COL_REGION)) null else getString(COL_REGION),
                ),
            )
        }
    }

    companion object {
        const val ASSET = "places.sqlite"
        private const val DEFAULT_LIMIT = 20
        private const val MAX_LIMIT = 100
        private const val FALLBACK_LANGUAGE = "en"
        private const val MAX_CHAR = "􏿿" // U+10FFFF: upper bound for prefix ranges
        private const val KM_PER_DEGREE = 111.0
        private const val EARTH_RADIUS_KM = 6371.0
        private val SEARCH_WINDOWS_DEG = listOf(0.5, 2.0, 8.0)

        // Column positions of COLUMNS.
        private const val COL_ID = 0
        private const val COL_LAT = 1
        private const val COL_LON = 2
        private const val COL_ZONE = 3
        private const val COL_COUNTRY_CODE = 4
        private const val COL_NAME = 5
        private const val COL_COUNTRY = 6
        private const val COL_REGION = 7

        private const val COLUMNS = """
            c.id, c.latitude, c.longitude, c.timezone, c.country,
            COALESCE(n1.name, n2.name, c.name), COALESCE(k1.name, k2.name, k.name), COALESCE(r1.name, r2.name, r.name)
        """
        private const val JOINS = """
            JOIN country k ON k.code = c.country
            LEFT JOIN region r ON r.code = c.region
            LEFT JOIN place_name n1 ON n1.geoname_id = c.id AND n1.language = ?
            LEFT JOIN place_name n2 ON n2.geoname_id = c.id AND n2.language = '$FALLBACK_LANGUAGE'
            LEFT JOIN place_name k1 ON k1.geoname_id = k.geoname_id AND k1.language = ?
            LEFT JOIN place_name k2 ON k2.geoname_id = k.geoname_id AND k2.language = '$FALLBACK_LANGUAGE'
            LEFT JOIN place_name r1 ON r1.geoname_id = r.geoname_id AND r1.language = ?
            LEFT JOIN place_name r2 ON r2.geoname_id = r.geoname_id AND r2.language = '$FALLBACK_LANGUAGE'
        """

        /** "zh-Hans-CN" -> "zh", "ar-EG" -> "ar": names are stored per base language. */
        internal fun baseLanguage(tag: String) = tag.substringBefore('-').substringBefore('_').lowercase()

        internal fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
            return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
        }

        /**
         * Opens the database, first copying it out of the APK once per app install/update (SQLite cannot
         * read compressed assets). Kept in noBackupFilesDir: it is app data, not user data.
         */
        fun open(context: Context): PlaceDirectory {
            val app = context.applicationContext
            val stamp = app.packageManager.getPackageInfo(app.packageName, 0).lastUpdateTime
            val dir = File(app.noBackupFilesDir, "places").apply { mkdirs() }
            val target = File(dir, "places-$stamp.sqlite")
            if (!target.isFile) {
                val tmp = File(dir, "${target.name}.tmp")
                app.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                check(tmp.renameTo(target)) { "Cannot install places database" }
                dir.listFiles()?.filter { it != target }?.forEach { it.delete() }
            }
            val flags = SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS
            return PlaceDirectory(SQLiteDatabase.openDatabase(target.path, null, flags))
        }
    }
}
