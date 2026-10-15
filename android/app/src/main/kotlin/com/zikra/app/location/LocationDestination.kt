package com.zikra.app.location

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zikra.app.appContainer
import com.zikra.app.ui.LocationScreen
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId

/** Navigation entry of the location picker: wires the ViewModel and the permission request. */
@Composable
fun LocationDestination(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val viewModel: LocationViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LocationViewModel(
                    places = { container.places },
                    store = container.locationStore,
                    locator = container.deviceLocator,
                    io = Dispatchers.IO,
                    systemZone = { ZoneId.systemDefault().id },
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()
    LaunchedEffect(languageTag) { viewModel.setLanguage(languageTag) }
    // Asked only when the user taps the button, and only for APPROXIMATE location (ADR-018).
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onLocationPermissionResult(granted)
    }
    LocationScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onPick = viewModel::onPick,
        onUseDeviceLocation = {
            val coarse = Manifest.permission.ACCESS_COARSE_LOCATION
            if (ContextCompat.checkSelfPermission(context, coarse) == PackageManager.PERMISSION_GRANTED) {
                viewModel.onLocationPermissionResult(true)
            } else {
                permission.launch(coarse)
            }
        },
    )
}
