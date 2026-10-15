package com.zikra.tooling.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlacesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun altRow(vararg c: String) = c.joinToString("\t")

    @Test
    fun extractKeepsCurrentNamesAndDropsEndedColloquialHistoricAndOtherLanguages() {
        val alt = tmp.newFile("alternateNamesV2.txt")
        alt.writeText(
            listOf(
                altRow("1", "10", "en", "Current", "1", "", "", "", "", ""),
                altRow("2", "10", "en", "SinceYear", "", "", "", "", "1995", ""), // from only: still current
                altRow("3", "10", "en", "Ended", "", "", "", "", "", "1995"),
                altRow("4", "10", "en", "Slang", "", "", "1", "", "", ""),
                altRow("5", "10", "en", "Old", "", "", "", "1", "", ""),
                altRow("6", "10", "xx", "OtherLanguage", "", "", "", "", "", ""),
                altRow("7", "99", "en", "NotBundled", "", "", "", "", "", ""),
                altRow("8", "10", "link", "https://example.org", "", "", "", "", "", ""),
            ).joinToString("\n"),
        )
        val out = File(tmp.root, "names.tsv")
        PlacesExtract.run(alt, setOf(10L), out)
        assertEquals(
            listOf("10\ten\tCurrent\t1\t0", "10\ten\tSinceYear\t0\t0"),
            out.readLines().filter { it.isNotEmpty() }
        )
    }

    @Test
    fun extractIsDeterministic() {
        val alt = tmp.newFile("alt.txt")
        alt.writeText(
            listOf(
                altRow("2", "20", "ar", "B", "", "", "", "", "", ""),
                altRow("1", "10", "en", "A", "", "", "", "", "", ""),
            ).joinToString("\n"),
        )
        val a = File(tmp.root, "a.tsv").also { PlacesExtract.run(alt, setOf(10L, 20L), it) }
        val b = File(tmp.root, "b.tsv").also { PlacesExtract.run(alt, setOf(20L, 10L), it) }
        assertEquals(a.readText(), b.readText())
        assertTrue(a.readText().startsWith("10\t"))
    }

    @Test
    fun searchKeysCoverEveryWordStart() {
        assertEquals(
            listOf("makkah al mukarramah", "al mukarramah", "mukarramah"),
            Places.searchKeysOf("Makkah al-Mukarramah")
        )
        assertEquals(listOf("sao paulo", "paulo"), Places.searchKeysOf("São Paulo"))
    }

    private fun city(id: Long, country: String = "AA", zone: String = "UTC") =
        GeoCity(id, "City$id", "City$id", 1.0, 2.0, country, "01", 0, zone)

    @Test
    fun displayNamePrefersPreferredThenNonShort() {
        val cities = List(Places.MIN_CITIES) { city(it.toLong() + 1) }
        val names = listOf(
            GeoName(1, "en", "Short", preferred = true, short = true),
            GeoName(1, "en", "Plain", preferred = false, short = false),
            GeoName(1, "en", "Best", preferred = true, short = false),
            GeoName(2, "en", "OnlyShort", preferred = false, short = true),
        )
        val places = Places.assemble(cities, listOf(GeoCountry("AA", "Country", 900)), emptyList(), names)
        val display = places.displayNames.associate { it.geonameId to it.name }
        assertEquals("Best", display[1])
        assertEquals("OnlyShort", display[2])
        // Every name (not only the display one) is searchable.
        assertTrue(places.searchKeys.contains("plain" to 1L))
    }

    @Test
    fun invalidCityDataStopsTheBuild() {
        val country = listOf(GeoCountry("AA", "Country", 900))
        val few = List(10) { city(it.toLong() + 1) }
        assertThrows(PipelineException::class.java) { Places.assemble(few, country, emptyList(), emptyList()) }
        val badZone = List(Places.MIN_CITIES) { city(it.toLong() + 1, zone = if (it == 0) "Mars/Base" else "UTC") }
        assertThrows(PipelineException::class.java) { Places.assemble(badZone, country, emptyList(), emptyList()) }
        val badCountry = List(Places.MIN_CITIES) { city(it.toLong() + 1, country = if (it == 0) "ZZ" else "AA") }
        assertThrows(PipelineException::class.java) { Places.assemble(badCountry, country, emptyList(), emptyList()) }
    }
}
