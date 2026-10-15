package com.zikra.app.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.ui.graphics.vector.ImageVector
import com.zikra.app.R
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute

@Serializable
data object MoreRoute

@Serializable
data object LanguageRoute

@Serializable
data object AccountRoute

@Serializable
data object SignInRoute

/**
 * Tabs of the bottom bar / navigation rail, in reading order (the first one sits at the start edge,
 * i.e. on the right in Arabic). A feature adds its tab only when it is implemented, so the bar never
 * shows a destination that does nothing (MASTER_BUILD_PROMPT §7, ADR-017).
 * Planned: Worship and Quran (Phase 2), Library (Phase 3). At most 5 tabs (DESIGN_SYSTEM_AR.md).
 */
enum class TopLevelDestination(
    val route: Any,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    TODAY(HomeRoute, R.string.nav_today, Icons.Filled.Home),
    MORE(MoreRoute, R.string.nav_more, Icons.Filled.Menu),
}
