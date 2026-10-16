package com.zikra.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.zikra.app.prayer.PrayerDestination
import com.zikra.app.prayer.PrayerMonthDestination
import com.zikra.app.prayer.PrayerSettingsDestination
import com.zikra.app.qibla.QiblaDestination
import com.zikra.app.quran.AboutQuranTextDestination
import com.zikra.app.quran.QuranBookmarksDestination
import com.zikra.app.quran.QuranIndexDestination
import com.zikra.app.quran.QuranReaderDestination
import com.zikra.app.quran.QuranSearchDestination

/** Prayer tab and its screens (WP 2.3-2.6). */
fun NavGraphBuilder.prayerGraph(navController: NavHostController) {
    composable<PrayerRoute> {
        PrayerDestination(
            onChooseLocation = { navController.navigate(LocationRoute) },
            onOpenSettings = { navController.navigate(PrayerSettingsRoute) },
            onOpenMonth = { navController.navigate(PrayerMonthRoute) },
            onOpenQibla = { navController.navigate(QiblaRoute) },
        )
    }
    composable<PrayerSettingsRoute> { PrayerSettingsDestination(onBack = { navController.popBackStack() }) }
    composable<PrayerMonthRoute> { PrayerMonthDestination(onBack = { navController.popBackStack() }) }
    composable<QiblaRoute> {
        QiblaDestination(
            onBack = { navController.popBackStack() },
            onChooseLocation = { navController.navigate(LocationRoute) },
        )
    }
}

/** Quran tab and its screens (WP 2.10-2.11). */
fun NavGraphBuilder.quranGraph(navController: NavHostController) {
    composable<QuranRoute> {
        QuranIndexDestination(
            onOpen = { surah, ayah -> navController.navigate(QuranReaderRoute(surah, ayah)) },
            onAbout = { navController.navigate(AboutQuranTextRoute) },
            onSearch = { navController.navigate(QuranSearchRoute) },
            onBookmarks = { navController.navigate(QuranBookmarksRoute) },
        )
    }
    composable<QuranBookmarksRoute> {
        QuranBookmarksDestination(
            onOpen = { surah, ayah -> navController.navigate(QuranReaderRoute(surah, ayah)) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<QuranSearchRoute> {
        QuranSearchDestination(
            onOpen = { surah, ayah -> navController.navigate(QuranReaderRoute(surah, ayah)) },
            onBack = { navController.popBackStack() },
        )
    }
    composable<QuranReaderRoute> { entry ->
        val route = entry.toRoute<QuranReaderRoute>()
        QuranReaderDestination(
            surah = route.surah,
            ayah = route.ayah,
            onBack = { navController.popBackStack() },
            onAbout = { navController.navigate(AboutQuranTextRoute) },
        )
    }
    composable<AboutQuranTextRoute> { AboutQuranTextDestination(onBack = { navController.popBackStack() }) }
}
