package com.zikra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zikra.app.R
import com.zikra.app.location.CurrentLocation
import com.zikra.app.location.LocateStatus
import com.zikra.app.location.LocationUiState
import com.zikra.core.designsystem.theme.ZikraSpacing
import com.zikra.core.i18n.Bidi
import com.zikra.core.places.Place
import com.zikra.core.settings.LocationSource

/**
 * Location picker (WP 2.1). Search works without any permission; the device-location button asks for
 * APPROXIMATE location only, when tapped (never on screen open).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationScreen(
    state: LocationUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onPick: (Place) -> Unit,
    onUseDeviceLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(text = stringResource(R.string.location_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                }
            },
        )
        LazyColumn(Modifier.fillMaxSize()) {
            item { CurrentLocationCard(state) }
            item { DeviceLocationSection(state.status, onUseDeviceLocation) }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text(text = stringResource(R.string.location_search_label)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ZikraSpacing.screenGutter)
                        .testTag(SEARCH_FIELD_TAG),
                )
            }
            items(state.results, key = { it.cityId }) { place ->
                ListItem(
                    headlineContent = { Text(text = place.name) },
                    supportingContent = { Text(text = placeDetails(place)) },
                    modifier = Modifier
                        .testTag(RESULT_TAG)
                        .clickable(role = Role.Button) { onPick(place) },
                )
            }
            if (state.query.isNotBlank() && !state.searching && state.results.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.location_no_results),
                        modifier = Modifier
                            .padding(ZikraSpacing.screenGutter)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.location_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(ZikraSpacing.screenGutter),
                )
            }
        }
    }
}

@Composable
private fun CurrentLocationCard(state: LocationUiState) {
    OutlinedCard(Modifier.fillMaxWidth().padding(ZikraSpacing.screenGutter)) {
        Column(Modifier.padding(ZikraSpacing.screenGutter), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.location_current_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = state.current
            when {
                !state.loaded -> Unit
                current == null -> Text(text = stringResource(R.string.location_not_set))
                else -> CurrentLocationText(current)
            }
        }
    }
}

@Composable
private fun CurrentLocationText(current: CurrentLocation) {
    val place = current.place
    val title = when {
        place == null -> stringResource(R.string.location_approximate)
        current.source == LocationSource.DEVICE -> stringResource(R.string.location_near_city, Bidi.isolate(place.name))
        else -> place.name
    }
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.testTag(CURRENT_LOCATION_TAG),
    )
    if (place != null) Text(text = placeDetails(place), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun DeviceLocationSection(status: LocateStatus, onUseDeviceLocation: () -> Unit) {
    Column(
        Modifier.padding(horizontal = ZikraSpacing.screenGutter).padding(bottom = ZikraSpacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalButton(onClick = onUseDeviceLocation, enabled = status != LocateStatus.LOCATING) {
            Icon(Icons.Filled.LocationOn, contentDescription = null)
            Text(text = stringResource(R.string.location_use_device), modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = stringResource(R.string.location_use_device_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val message = when (status) {
            LocateStatus.IDLE, LocateStatus.LOCATING -> null
            LocateStatus.PERMISSION_DENIED -> R.string.location_denied
            LocateStatus.UNAVAILABLE -> R.string.location_unavailable
        }
        if (status == LocateStatus.LOCATING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(text = stringResource(R.string.location_locating))
            }
        }
        message?.let {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** "Region, Country" (or just the country), each part isolated so mixed scripts keep their order. */
@Composable
private fun placeDetails(place: Place): String {
    val region = place.region?.takeIf { it != place.name && it != place.country }
    return if (region == null) {
        place.country
    } else {
        stringResource(R.string.location_place_details, Bidi.isolate(region), Bidi.isolate(place.country))
    }
}

internal const val SEARCH_FIELD_TAG = "location_search"
internal const val CURRENT_LOCATION_TAG = "location_current"
internal const val RESULT_TAG = "location_result"
