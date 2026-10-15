package com.zikra.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

sealed interface LocateResult {
    data class Found(val latitude: Double, val longitude: Double) : LocateResult

    /** Location services are off, or no provider produced a fix in time. */
    data object Unavailable : LocateResult

    data object NoPermission : LocateResult
}

/** One-shot APPROXIMATE device location (ADR-018). Never tracks, never runs in the background. */
fun interface DeviceLocator {
    suspend fun approximateLocation(): LocateResult
}

/**
 * Platform LocationManager only: no Google Play services, so it also works on devices without them.
 * The app holds ACCESS_COARSE_LOCATION only, so every fix is approximate (about 2 km or coarser).
 */
class AndroidDeviceLocator(context: Context) : DeviceLocator {
    private val context = context.applicationContext

    override suspend fun approximateLocation(): LocateResult {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = context.getSystemService(LocationManager::class.java)
        return when {
            granted != PackageManager.PERMISSION_GRANTED -> LocateResult.NoPermission
            manager == null || !LocationManagerCompat.isLocationEnabled(manager) -> LocateResult.Unavailable
            else -> try {
                val fix = providers(manager).firstNotNullOfOrNull { currentFix(manager, it) } ?: recentFix(manager)
                fix?.let { LocateResult.Found(it.latitude, it.longitude) } ?: LocateResult.Unavailable
            } catch (_: SecurityException) {
                LocateResult.NoPermission // revoked while we were asking
            }
        }
    }

    private fun providers(manager: LocationManager): List<String> = buildList {
        add(LocationManager.NETWORK_PROVIDER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
    }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }

    // Permission is checked in approximateLocation(); a revocation in between surfaces as SecurityException.
    @SuppressLint("MissingPermission")
    private suspend fun currentFix(manager: LocationManager, provider: String): Location? =
        withTimeoutOrNull(FIX_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { location -> if (cont.isActive) cont.resume(location) }
            }
        }

    @SuppressLint("MissingPermission")
    private fun recentFix(manager: LocationManager): Location? =
        manager.getProviders(true)
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < MAX_LAST_KNOWN_AGE_MS }
            .maxByOrNull { it.time }

    private companion object {
        const val FIX_TIMEOUT_MS = 20_000L
        const val MAX_LAST_KNOWN_AGE_MS = 30 * 60 * 1000L // older fixes may predate a flight
    }
}
