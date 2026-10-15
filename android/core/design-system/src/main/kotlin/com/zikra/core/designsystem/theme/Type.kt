package com.zikra.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Type scale from tokens.json (sizes in sp, so they follow the system font size up to 200%).
 * Line height depends on the script (DESIGN_SYSTEM_TOKENS §2): Arabic-script text needs more room
 * for diacritics. The UI uses the system font: on Android that is Noto (Sans/Naskh Arabic for
 * Arabic). The licensed Quran font is separate and only for mushaf text (ADR-P05).
 */
internal fun zikraTypography(arabicScript: Boolean): Typography {
    val lineHeight = if (arabicScript) ARABIC_LINE_HEIGHT else LATIN_LINE_HEIGHT

    fun style(size: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = (size * lineHeight).sp,
    )

    return Typography(
        displaySmall = style(48, FontWeight.Medium),
        headlineLarge = style(28, FontWeight.Bold),
        headlineSmall = style(22, FontWeight.Bold),
        titleLarge = style(20, FontWeight.Bold),
        titleMedium = style(16, FontWeight.Medium),
        bodyLarge = style(16),
        bodyMedium = style(14),
        labelLarge = style(14, FontWeight.Medium),
        labelSmall = style(12, FontWeight.Medium),
    )
}

private const val LATIN_LINE_HEIGHT = 1.4f
private const val ARABIC_LINE_HEIGHT = 1.6f
