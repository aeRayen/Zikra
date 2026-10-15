package com.zikra.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Crescent icon for the prayer tab. Path from Material Symbols «dark_mode» (Apache License 2.0),
 * so the tab does not need the large material-icons-extended dependency.
 */
val PrayerTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PrayerTab",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
        .addPath(
            pathData = addPathNodes(
                "M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9s9,-4.03 9,-9c0,-0.46 -0.04,-0.92 -0.1,-1.36" +
                    "c-0.98,1.37 -2.58,2.26 -4.4,2.26c-2.98,0 -5.4,-2.42 -5.4,-5.4c0,-1.81 0.89,-3.42 2.26,-4.4" +
                    "C12.92,3.04 12.46,3 12,3z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}
