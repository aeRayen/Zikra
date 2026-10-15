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
 * reviewed here first. Today the app has NO network permission, so the OS itself guarantees that
 * nothing leaves the device (WP 1.13). INTERNET arrives with content download (Phase 2) and from
 * then on NetworkGate (core:network) enforces guest mode.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsAllowlistTest {

    private val reviewed = setOf(
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
