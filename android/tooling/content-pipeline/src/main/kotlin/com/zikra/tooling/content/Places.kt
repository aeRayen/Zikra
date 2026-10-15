package com.zikra.tooling.content

import com.zikra.core.i18n.SearchNormalizer
import java.time.ZoneId

/** Everything written to places.sqlite, assembled and validated from the pinned GeoNames files. */
class Places(
    val cities: List<GeoCity>,
    val countries: List<GeoCountry>,
    val regions: List<GeoAdmin1>,
    val displayNames: List<GeoName>,
    /** (normalized key, city id). */
    val searchKeys: List<Pair<String, Long>>,
) {
    private val regionCodes = regions.mapTo(HashSet()) { it.code }

    fun regionCode(city: GeoCity): String? = "${city.country}.${city.admin1}".takeIf { it in regionCodes }

    companion object {
        const val MIN_CITIES = 30_000
        private const val MAX_LAT = 90.0
        private const val MAX_LON = 180.0
        private val wordBreak = Regex("[\\s\\-'’‘`]+")

        fun assemble(
            cities: List<GeoCity>,
            countries: List<GeoCountry>,
            admin1: List<GeoAdmin1>,
            names: List<GeoName>,
        ): Places {
            validateCities(cities, countries)
            val usedCountries = cities.mapTo(HashSet()) { it.country }
            val keptCountries = countries.filter { it.code in usedCountries }.sortedBy { it.code }
            val usedRegions = cities.mapTo(HashSet()) { "${it.country}.${it.admin1}" }
            val keptRegions = admin1.filter { it.code in usedRegions }.sortedBy { it.code }
            val cityIds = cities.mapTo(HashSet()) { it.id }
            val keptIds = cityIds + keptCountries.map { it.geonameId } + keptRegions.map { it.geonameId }
            val byPlace = names.filter { it.geonameId in keptIds }.groupBy { it.geonameId }
            return Places(
                cities = cities.sortedBy { it.id },
                countries = keptCountries,
                regions = keptRegions,
                displayNames = byPlace.values.flatMap { displayNamesOf(it) }
                    .sortedWith(compareBy({ it.geonameId }, { it.language })),
                searchKeys = cities.flatMap { city ->
                    val all = listOf(city.name, city.asciiName) + byPlace[city.id].orEmpty().map { it.name }
                    all.flatMap { searchKeysOf(it) }.distinct().map { it to city.id }
                }.distinct().sortedWith(compareBy({ it.first }, { it.second })),
            )
        }

        /** One display name per language: preferred non-short, then any non-short, then any. */
        private fun displayNamesOf(names: List<GeoName>): List<GeoName> =
            names.groupBy { it.language }.values.map { forLanguage ->
                forLanguage.firstOrNull { it.preferred && !it.short }
                    ?: forLanguage.firstOrNull { !it.short }
                    ?: forLanguage.first()
            }

        /** Normalized full name plus every suffix that starts a word, so "mukarramah" finds "Makkah al Mukarramah". */
        fun searchKeysOf(name: String): List<String> {
            val words = SearchNormalizer.normalize(name).split(wordBreak).filter { it.isNotEmpty() }
            return words.indices.map { i -> words.subList(i, words.size).joinToString(" ") }
        }

        private fun validateCities(cities: List<GeoCity>, countries: List<GeoCountry>) {
            ensure(cities.size >= MIN_CITIES) { "Only ${cities.size} cities" }
            ensure(cities.map { it.id }.toSet().size == cities.size) { "Duplicate city ids" }
            val countryCodes = countries.mapTo(HashSet()) { it.code }
            val zones = ZoneId.getAvailableZoneIds()
            cities.forEach { c ->
                val inRange = c.latitude in -MAX_LAT..MAX_LAT && c.longitude in -MAX_LON..MAX_LON
                ensure(inRange) { "Bad coordinates ${c.id}" }
                ensure(c.country in countryCodes) { "Unknown country ${c.country} for ${c.id}" }
                ensure(c.timezone in zones) { "Unknown time zone ${c.timezone} for ${c.id}" }
                ensure(c.name.isNotBlank()) { "City ${c.id} has no name" }
            }
        }
    }
}
