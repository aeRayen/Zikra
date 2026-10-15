package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance for WP 1.13: a guest produces zero user-data or account requests. */
class GuestModeTest {

    private class CountingTransport : Transport {
        val sent = mutableListOf<NetworkRequest>()
        override suspend fun send(request: NetworkRequest): AppResult<ByteArray> {
            sent += request
            return AppResult.Success(ByteArray(0))
        }
    }

    private val allSyncPurposes = SyncConsent.entries.map { NetworkPurpose.UserDataSync(it) }

    private fun transport(session: Session): Pair<GatedTransport, CountingTransport> {
        val counting = CountingTransport()
        return GatedTransport(NetworkGate(SessionHolder(session)), counting) to counting
    }

    @Test
    fun guestNeverReachesAccountOrSyncBackend() = runTest {
        val (gated, counting) = transport(Session.Guest)
        (allSyncPurposes + NetworkPurpose.Account).forEach { purpose ->
            val result = gated.send(NetworkRequest(purpose, "POST", "https://api.example.invalid"))
            assertTrue("$purpose must be refused for a guest", result is AppResult.Failure)
        }
        assertEquals("no request may leave the device", 0, counting.sent.size)
    }

    @Test
    fun guestMayStartASignIn() = runTest {
        val (gated, counting) = transport(Session.Guest)
        assertTrue(gated.send(NetworkRequest(NetworkPurpose.SignIn, "POST", "x")) is AppResult.Success)
        assertEquals(1, counting.sent.size)
    }

    @Test
    fun guestMayDownloadPublicContentOnly() = runTest {
        val (gated, counting) = transport(Session.Guest)
        gated.send(NetworkRequest(NetworkPurpose.PublicContent, "GET", "https://cdn.example.invalid/pack"))
        assertEquals(listOf(NetworkPurpose.PublicContent), counting.sent.map { it.purpose })
    }

    @Test
    fun signedInUserSyncsOnlyConsentedCategories() = runTest {
        val (gated, counting) = transport(Session.SignedIn("u1", setOf(SyncConsent.BOOKMARKS)))
        val denied = gated.send(NetworkRequest(NetworkPurpose.UserDataSync(SyncConsent.WORSHIP), "POST", "x"))
        gated.send(NetworkRequest(NetworkPurpose.UserDataSync(SyncConsent.BOOKMARKS), "POST", "x"))

        assertEquals(AppResult.Failure(AppError.ConsentRequired), denied)
        val sentCategories = counting.sent.map { (it.purpose as NetworkPurpose.UserDataSync).category }
        assertEquals(listOf(SyncConsent.BOOKMARKS), sentCategories)
    }

    @Test
    fun signedInUserCanUseAccountEndpoints() = runTest {
        val (gated, counting) = transport(Session.SignedIn("u1", emptySet()))
        assertTrue(gated.send(NetworkRequest(NetworkPurpose.Account, "POST", "x")) is AppResult.Success)
        assertEquals(1, counting.sent.size)
    }

    @Test
    fun signingOutReturnsToZeroRequests() = runTest {
        val holder = SessionHolder(Session.SignedIn("u1", SyncConsent.entries.toSet()))
        val counting = CountingTransport()
        val gated = GatedTransport(NetworkGate(holder), counting)
        gated.send(NetworkRequest(NetworkPurpose.Account, "POST", "x"))

        holder.update(Session.Guest)
        (allSyncPurposes + NetworkPurpose.Account).forEach { gated.send(NetworkRequest(it, "POST", "x")) }
        assertEquals(1, counting.sent.size)
    }
}
