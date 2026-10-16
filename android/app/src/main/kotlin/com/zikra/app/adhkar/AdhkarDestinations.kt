package com.zikra.app.adhkar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.AdhkarChapterActions
import com.zikra.app.ui.AdhkarChapterScreen
import com.zikra.app.ui.AdhkarIndexScreen
import com.zikra.core.adhkar.Dhikr
import kotlinx.coroutines.Dispatchers

@Composable
fun AdhkarIndexDestination(onOpen: (Int) -> Unit) {
    val container = appContainer()
    val viewModel: AdhkarIndexViewModel = viewModel(
        factory = viewModelFactory { initializer { AdhkarIndexViewModel({ container.adhkar }, Dispatchers.IO) } },
    )
    val state by viewModel.uiState.collectAsState()
    AdhkarIndexScreen(state = state, onOpen = onOpen, onQueryChange = viewModel::onQueryChange)
}

@Composable
fun AdhkarChapterDestination(chapter: Int, onBack: () -> Unit) {
    val container = appContainer()
    val viewModel: AdhkarChapterViewModel = viewModel(
        key = "adhkar-$chapter",
        factory = viewModelFactory {
            initializer {
                AdhkarChapterViewModel({ container.adhkar }, container.adhkarProgress, Dispatchers.IO, chapter)
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
