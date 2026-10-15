package com.zikra.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Spacing on a 4dp grid (tokens.json → spacingDp). Use these instead of raw dp values. */
object ZikraSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Side margin of every screen. */
    val screenGutter = 16.dp
}

object ZikraSizes {
    /** Minimum touch target (accessibility). */
    val minTouchTarget = 48.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
}

/** tokens.json → radiusDp: fields 12, cards 16, buttons 24. */
internal val ZikraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
