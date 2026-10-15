package com.zikra.app.qibla

import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.QiblaScreen
import kotlinx.coroutines.Dispatchers

/** Navigation entry of the Qibla screen. The compass runs only while the screen is resumed. */
@Composable
fun QiblaDestination(onBack: () -> Unit, onChooseLocation: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val view = LocalView.current
    val viewModel: QiblaViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                QiblaViewModel(
                    places = { container.places },
                    location = container.locationStore,
                    compass = AndroidCompass(context.applicationContext),
                    io = Dispatchers.IO,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }
    LifecycleResumeEffect(Unit) {
        viewModel.startCompass { displayRotation(view) }
        onPauseOrDispose { viewModel.stopCompass() }
    }
    QiblaScreen(state = state, onBack = onBack, onChooseLocation = onChooseLocation)
}

private fun displayRotation(view: android.view.View): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        view.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (view.context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation) ?: Surface.ROTATION_0
    }
