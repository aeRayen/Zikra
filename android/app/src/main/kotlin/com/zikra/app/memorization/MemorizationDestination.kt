package com.zikra.app.memorization

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.MemorizationActions
import com.zikra.app.ui.MemorizationScreen
import com.zikra.core.memorization.Direction
import com.zikra.core.memorization.Pace
import com.zikra.core.memorization.Recall
import kotlinx.coroutines.Dispatchers

@Composable
fun MemorizationDestination(onBack: () -> Unit, onOpenPage: (Int, Int) -> Unit) {
    val container = appContainer()
    val viewModel: MemorizationViewModel = viewModel(
        factory = viewModelFactory {
            initializer { MemorizationViewModel(container.memorization, { container.quran }, Dispatchers.IO) }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val saveFailed by viewModel.saveFailed.collectAsState()
    MemorizationScreen(
        state = state,
        saveFailed = saveFailed,
        actions = object : MemorizationActions {
            override val onBack = onBack
            override val onSetPlan: (Direction, Pace) -> Unit = viewModel::setPlan
            override val onOpenPage = onOpenPage
            override val onMemorized: (Int) -> Unit = viewModel::memorized
            override val onReviewed: (Int, Recall) -> Unit = viewModel::reviewed
            override val onMemorizedJuz: (Int) -> Unit = viewModel::memorizedJuz
        },
    )
}
