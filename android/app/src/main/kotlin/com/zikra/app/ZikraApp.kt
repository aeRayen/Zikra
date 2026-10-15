package com.zikra.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zikra.app.i18n.AppLocales
import com.zikra.app.navigation.HomeRoute
import com.zikra.app.navigation.LanguageRoute
import com.zikra.app.navigation.MoreRoute
import com.zikra.app.navigation.TopLevelDestination
import com.zikra.app.ui.HomeScreen
import com.zikra.app.ui.LanguageScreen
import com.zikra.app.ui.MoreScreen

/**
 * App shell: bottom bar on phones (compact width), navigation rail on tablets/foldables.
 * Layout direction follows the locale; nothing here uses left/right.
 */
@Composable
fun ZikraApp(
    widthSizeClass: WindowWidthSizeClass,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination
    val onTab: (TopLevelDestination) -> Unit = { tab -> navController.navigateToTab(tab) }
    val useRail = widthSizeClass != WindowWidthSizeClass.Compact

    Row(Modifier.fillMaxSize()) {
        if (useRail) TabRail(current, onTab)
        Scaffold(bottomBar = { if (!useRail) TabBar(current, onTab) }) { padding ->
            ZikraNavHost(navController, Modifier.padding(padding))
        }
    }
}

@Composable
private fun ZikraNavHost(navController: NavHostController, modifier: Modifier) {
    NavHost(navController = navController, startDestination = HomeRoute, modifier = modifier) {
        composable<HomeRoute> { HomeScreen() }
        composable<MoreRoute> { MoreScreen(onOpenLanguage = { navController.navigate(LanguageRoute) }) }
        composable<LanguageRoute> {
            LanguageScreen(
                selected = AppLocales.current(),
                onSelect = AppLocales::set,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun TabBar(current: NavDestination?, onTab: (TopLevelDestination) -> Unit) {
    NavigationBar {
        TopLevelDestination.entries.forEach { tab ->
            NavigationBarItem(
                selected = current.isOn(tab),
                onClick = { onTab(tab) },
                modifier = Modifier.testTag(tabTag(tab)),
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) },
            )
        }
    }
}

@Composable
private fun TabRail(current: NavDestination?, onTab: (TopLevelDestination) -> Unit) {
    NavigationRail {
        TopLevelDestination.entries.forEach { tab ->
            NavigationRailItem(
                selected = current.isOn(tab),
                onClick = { onTab(tab) },
                modifier = Modifier.testTag(tabTag(tab)),
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) },
            )
        }
    }
}

private fun NavDestination?.isOn(tab: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.route::class) } == true

/** Tab switch keeps one copy of each tab on the back stack and restores its state. */
private fun NavHostController.navigateToTab(tab: TopLevelDestination) {
    navigate(tab.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Stable test tag of a navigation tab (tests must not depend on translated labels). */
internal fun tabTag(tab: TopLevelDestination) = "tab_${tab.name}"
