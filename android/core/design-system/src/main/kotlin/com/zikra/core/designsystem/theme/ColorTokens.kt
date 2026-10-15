package com.zikra.core.designsystem.theme

/**
 * Colour tokens, copied from docs/specs/design/tokens.json (the source of truth).
 * TokensSyncTest fails if these maps drift from the JSON; ContrastTest checks WCAG AA pairs.
 */
internal object ColorTokens {
    val light: Map<String, Long> = mapOf(
        "background" to 0xFFFBF8F1,
        "onBackground" to 0xFF1B1C19,
        "surface" to 0xFFFFFFFF,
        "onSurface" to 0xFF1B1C19,
        "surfaceVariant" to 0xFFF2EDE1,
        "onSurfaceVariant" to 0xFF47504A,
        "primary" to 0xFF0E5A45,
        "onPrimary" to 0xFFFFFFFF,
        "primaryContainer" to 0xFFCDE8DC,
        "onPrimaryContainer" to 0xFF002117,
        "secondary" to 0xFF7A5C14,
        "onSecondary" to 0xFFFFFFFF,
        "secondaryContainer" to 0xFFF3E3B5,
        "onSecondaryContainer" to 0xFF2A1F00,
        "outline" to 0xFF737B75,
        "error" to 0xFFB3261E,
        "onError" to 0xFFFFFFFF,
        "warning" to 0xFF7A4F00,
        "success" to 0xFF1E6B3A,
        "quranPage" to 0xFFF7F1E3,
        "onQuranPage" to 0xFF1F3A2E,
    )

    val dark: Map<String, Long> = mapOf(
        "background" to 0xFF0A1F1A,
        "onBackground" to 0xFFECF2EE,
        "surface" to 0xFF0F2620,
        "onSurface" to 0xFFECF2EE,
        "surfaceVariant" to 0xFF1A3A30,
        "onSurfaceVariant" to 0xFFB9C9C0,
        "primary" to 0xFF7FD3B0,
        "onPrimary" to 0xFF003827,
        "primaryContainer" to 0xFF0E5A45,
        "onPrimaryContainer" to 0xFFCDE8DC,
        "secondary" to 0xFFE5CC85,
        "onSecondary" to 0xFF3A2E00,
        "secondaryContainer" to 0xFF5A4510,
        "onSecondaryContainer" to 0xFFF3E3B5,
        "outline" to 0xFF8A9C93,
        "error" to 0xFFF2B8B5,
        "onError" to 0xFF601410,
        "warning" to 0xFFF0C070,
        "success" to 0xFF8FD6A4,
        "quranPage" to 0xFF14241F,
        "onQuranPage" to 0xFFE8E0C8,
    )
}
