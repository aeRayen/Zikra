package com.zikra.core.designsystem.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2.x contrast for every text/background pair the components use (DESIGN_SYSTEM_TOKENS §1). */
class ContrastTest {

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun ratio(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private val textPairs = listOf(
        "onBackground" to "background",
        "onSurface" to "surface",
        "onSurfaceVariant" to "surface",
        "onSurfaceVariant" to "surfaceVariant",
        "onSurface" to "surfaceVariant", // banner message
        "primary" to "background",
        "primary" to "surface",
        "onPrimary" to "primary",
        "onPrimaryContainer" to "primaryContainer",
        "secondary" to "surface", // card labels
        "onSecondaryContainer" to "secondaryContainer",
        "error" to "surface",
        "onError" to "error",
        "warning" to "surfaceVariant", // banner action
        "onQuranPage" to "quranPage",
    )

    private fun check(themeName: String, t: Map<String, Long>) {
        textPairs.forEach { (fg, bg) ->
            val r = ratio(t.getValue(fg), t.getValue(bg))
            assertTrue("$themeName: $fg on $bg = ${"%.2f".format(r)} < 4.5", r >= 4.5)
        }
        val border = ratio(t.getValue("outline"), t.getValue("surface"))
        assertTrue("$themeName: outline on surface = $border < 3.0", border >= 3.0)
    }

    @Test
    fun lightThemeMeetsAa() = check("light", ColorTokens.light)

    @Test
    fun darkThemeMeetsAa() = check("dark", ColorTokens.dark)
}
