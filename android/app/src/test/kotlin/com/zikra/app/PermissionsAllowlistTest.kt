package com.zikra.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every Android permission in the merged manifest (ours and any a library adds silently) must be
 * reviewed here first. INTERNET was added in WP 1.8; since then guest mode (no account or sync
 * traffic) is enforced by NetworkGate in core:network rather than by the missing permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsAllowlistTest {

    private val reviewed = setOf(
        // WP 1.8 (reviewed): optional account + sync and content packs. Guest mode is enforced by
        // NetworkGate, see GuestModeTest and SupabaseClientTest.guestIsStoppedBeforeTheNetwork.
        "android.permission.INTERNET",
        // WP 2.1 (reviewed, ADR-018): APPROXIMATE location only, asked on tap, one-shot, kept on device.
        // ACCESS_FINE_LOCATION and ACCESS_BACKGROUND_LOCATION must never appear here.
        "android.permission.ACCESS_COARSE_LOCATION",
        // WP 2.4 (reviewed, ADR-033): re-arm alarms after reboot; on-time prayer alerts. Exact alarms are
        // user-grantable with an inexact fallback. USE_EXACT_ALARM must never appear here (Play policy).
        "android.permission.RECEIVE_BOOT_COMPLETED",
        "android.permission.SCHEDULE_EXACT_ALARM",
        // WP 2.5 (reviewed): prayer-time alerts; asked on Android 13+ with an explanation, never on first launch.
        "android.permission.POST_NOTIFICATIONS",
        // Added by androidx.core for non-exported dynamic receivers; signature-level, app-private.
        "com.zikra.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
    )

    @Test
    fun mergedManifestRequestsOnlyReviewedPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toSet()
        assertEquals("Review new permissions (privacy, Play policy) before adding them here", reviewed, requested)
    }
}
