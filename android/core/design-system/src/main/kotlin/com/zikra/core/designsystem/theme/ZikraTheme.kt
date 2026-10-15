package com.zikra.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import com.zikra.core.i18n.SupportedLanguages
import com.zikra.core.i18n.TextDirection

/**
 * App theme. Brand colours only (no dynamic colour), light/dark following the system unless
 * [darkTheme] is given. Layout direction comes from the locale (supportsRtl), never set by hand.
 */
@Composable
fun ZikraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val tokens = if (darkTheme) ColorTokens.dark else ColorTokens.light
    val language = LocalConfiguration.current.locales[0]?.language.orEmpty()
    val arabicScript = SupportedLanguages.directionOf(language) == TextDirection.RTL

    CompositionLocalProvider(LocalZikraExtendedColors provides extendedColorsOf(tokens)) {
        MaterialTheme(
            colorScheme = colorSchemeOf(tokens, darkTheme),
            typography = zikraTypography(arabicScript),
            shapes = ZikraShapes,
            content = content,
        )
    }
}

/** Access to colours outside Material 3: `ZikraTheme.extendedColors.warning`. */
object ZikraTheme {
    val extendedColors: ZikraExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalZikraExtendedColors.current
}
