package com.zikra.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours outside the Material 3 scheme. Never use colour alone to carry meaning (DESIGN_SYSTEM_TOKENS §1). */
@Immutable
data class ZikraExtendedColors(
    val warning: Color,
    val success: Color,
    val quranPage: Color,
    val onQuranPage: Color,
)

internal val LocalZikraExtendedColors = staticCompositionLocalOf {
    extendedColorsOf(ColorTokens.light)
}

private fun Map<String, Long>.color(name: String) = Color(getValue(name))

internal fun extendedColorsOf(t: Map<String, Long>) = ZikraExtendedColors(
    warning = t.color("warning"),
    success = t.color("success"),
    quranPage = t.color("quranPage"),
    onQuranPage = t.color("onQuranPage"),
)

internal fun colorSchemeOf(t: Map<String, Long>, dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = t.color("primary"),
            onPrimary = t.color("onPrimary"),
            primaryContainer = t.color("primaryContainer"),
            onPrimaryContainer = t.color("onPrimaryContainer"),
            secondary = t.color("secondary"),
            onSecondary = t.color("onSecondary"),
            secondaryContainer = t.color("secondaryContainer"),
            onSecondaryContainer = t.color("onSecondaryContainer"),
            tertiary = t.color("primary"),
            onTertiary = t.color("onPrimary"),
            background = t.color("background"),
            onBackground = t.color("onBackground"),
            surface = t.color("surface"),
            onSurface = t.color("onSurface"),
            surfaceVariant = t.color("surfaceVariant"),
            onSurfaceVariant = t.color("onSurfaceVariant"),
            surfaceTint = t.color("surface"), // flat surfaces, no tonal tint
            error = t.color("error"),
            onError = t.color("onError"),
            outline = t.color("outline"),
            outlineVariant = t.color("outline"),
        )
    } else {
        lightColorScheme(
            primary = t.color("primary"),
            onPrimary = t.color("onPrimary"),
            primaryContainer = t.color("primaryContainer"),
            onPrimaryContainer = t.color("onPrimaryContainer"),
            secondary = t.color("secondary"),
            onSecondary = t.color("onSecondary"),
            secondaryContainer = t.color("secondaryContainer"),
            onSecondaryContainer = t.color("onSecondaryContainer"),
            tertiary = t.color("primary"),
            onTertiary = t.color("onPrimary"),
            background = t.color("background"),
            onBackground = t.color("onBackground"),
            surface = t.color("surface"),
            onSurface = t.color("onSurface"),
            surfaceVariant = t.color("surfaceVariant"),
            onSurfaceVariant = t.color("onSurfaceVariant"),
            surfaceTint = t.color("surface"),
            error = t.color("error"),
            onError = t.color("onError"),
            outline = t.color("outline"),
            outlineVariant = t.color("outline"),
        )
    }
