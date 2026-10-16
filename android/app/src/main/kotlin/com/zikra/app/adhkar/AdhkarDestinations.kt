package com.zikra.app.adhkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.adhan.rememberAlertsNotice
import com.zikra.app.appContainer
import com.zikra.app.ui.AdhkarChapterActions
import com.zikra.app.ui.AdhkarChapterScreen
import com.zikra.app.ui.AdhkarIndexScreen
import com.zikra.app.ui.AdhkarRemindersActions
import com.zikra.app.ui.AdhkarRemindersScreen
import com.zikra.core.adhkar.Dhikr
import kotlinx.coroutines.Dispatchers

@Composable
fun AdhkarIndexDestination(onOpen: (Int) -> Unit, onReminders: () -> Unit) {
    val container = appContainer()
    val language = LocalConfiguration.current.locales[0].language
    val viewModel: AdhkarIndexViewModel = viewModel(
        key = "adhkar-index-$language",
        factory = viewModelFactory {
            initializer { AdhkarIndexViewModel({ container.adhkar }, Dispatchers.IO, language) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    AdhkarIndexScreen(
        state = state,
        onOpen = onOpen,
        onQueryChange = viewModel::onQueryChange,
        onReminders = onReminders,
    )
}

@Composable
fun AdhkarChapterDestination(chapter: Int, onBack: () -> Unit) {
    val container = appContainer()
    val language = LocalConfiguration.current.locales[0].language
    val viewModel: AdhkarChapterViewModel = viewModel(
        key = "adhkar-$chapter-$language",
        factory = viewModelFactory {
            initializer {
                AdhkarChapterViewModel(
                    { container.adhkar },
                    container.adhkarProgress,
                    Dispatchers.IO,
                    chapter,
                    language,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val counts by viewModel.counts.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    AdhkarChapterScreen(
        state = state,
        counts = counts,
        saveFailed = saveFailed,
        actions = object : AdhkarChapterActions {
            override val onBack = onBack
            override val onCount: (Dhikr) -> Unit = viewModel::count
            override val onReset = viewModel::reset
        },
    )
}

@Composable
fun AdhkarRemindersDestination(onBack: () -> Unit, onChooseLocation: () -> Unit) {
    val container = appContainer()
    val viewModel: AdhkarRemindersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AdhkarRemindersViewModel(container.adhkarReminders, container.locationStore) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    val alerts = rememberAlertsNotice()
    AdhkarRemindersScreen(
        state = state,
        alertsNotice = alerts.notice,
        saveFailed = saveFailed,
        actions = object : AdhkarRemindersActions {
            override val onBack = onBack
            override val onMorning: (Boolean) -> Unit = viewModel::setMorning
            override val onEvening: (Boolean) -> Unit = viewModel::setEvening
            override val onSleep: (Boolean) -> Unit = viewModel::setSleep
            override val onSleepTime: (Int, Int) -> Unit = viewModel::setSleepTime
            override val onChooseLocation = onChooseLocation
            override val onFixAlerts = alerts.fix
        },
    )
}
