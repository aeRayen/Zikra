package com.zikra.core.designsystem.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Fails when ColorTokens.kt drifts from docs/specs/design/tokens.json (the design source of truth). */
class TokensSyncTest {

    private val json: String by lazy {
        val path = requireNotNull(System.getProperty("zikra.tokensFile")) { "zikra.tokensFile not set" }
        File(path).readText()
    }

    /** Extracts {"name": "#RRGGBB"} pairs from the block that starts with "<theme>": { ... }. */
    private fun themeBlock(theme: String): Map<String, Long> {
        val start = json.indexOf("\"$theme\": {")
        assertTrue("theme '$theme' missing in tokens.json", start >= 0)
        val block = json.substring(start, json.indexOf('}', start))
        return Regex("\"(\\w+)\":\\s*\"#([0-9A-Fa-f]{6})\"").findAll(block).associate { m ->
            m.groupValues[1] to (0xFF000000 or m.groupValues[2].toLong(16))
        }
    }

    @Test
    fun lightColorsMatchTokensJson() = assertEquals(themeBlock("light"), ColorTokens.light)

    @Test
    fun darkColorsMatchTokensJson() = assertEquals(themeBlock("dark"), ColorTokens.dark)
}
