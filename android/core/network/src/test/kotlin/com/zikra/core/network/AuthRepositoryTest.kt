package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** WP 1.12: e-mail code sign-in, session storage, refresh and sign-out rules. */
class AuthRepositoryTest {

    private class MemoryStore : TokenStore {
        var saved: StoredSession? = null
        override fun load() = saved
        override fun save(session: StoredSession) {
            saved = session
        }
        override fun clear() {
            saved = null
        }
    }

    /** Fake server: answers by path; records what was called. */
    private class FakeServer : Transport {
        val calls = mutableListOf<String>()
        var refreshAnswer: AppResult<ByteArray> = ok(TOKENS.replace("\"a\"", "\"a2\""))
        var online = true
        override suspend fun send(request: NetworkRequest): AppResult<ByteArray> {
            val path = request.url.substringAfter("example.invalid")
            calls += path
            if (!online) return AppResult.Failure(AppError.Offline)
            return when {
                path.startsWith("/auth/v1/verify") -> ok(TOKENS)
                path.startsWith("/auth/v1/token") -> refreshAnswer
                else -> ok("{}")
            }
        }
    }

    private companion object {
        const val TOKENS = """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u1"}}"""
        fun ok(body: String): AppResult<ByteArray> = AppResult.Success(body.toByteArray())
    }

    private var now = 1_000_000L
    private val server = FakeServer()
    private val store = MemoryStore()
    private val sessions = SessionHolder()
    private val repo = AuthRepository(
        SupabaseClient(SupabaseConfig("https://example.invalid", "k"), GatedTransport(NetworkGate(sessions), server)),
        store,
        sessions,
        Clock { now },
    )

    @Test
    fun invalidInputNeverReachesTheServer() = runTest {
        assertEquals(AppResult.Failure(AppError.Validation(listOf("email"))), repo.requestCode("not-an-email"))
        assertEquals(AppResult.Failure(AppError.Validation(listOf("code"))), repo.verifyCode("a@b.co", "12ab"))
        assertTrue(server.calls.isEmpty())
    }

    @Test
    fun verifyingTheCodeSignsInAndStoresTheSession() = runTest {
        assertTrue(repo.verifyCode("  A@Example.COM ", "123456") is AppResult.Success)
        assertEquals(Session.SignedIn("u1", emptySet()), sessions.session.value)
        assertEquals("a@example.com", store.saved!!.email)
        assertEquals(now + 3600, store.saved!!.expiresAtEpochSeconds)
    }

    @Test
    fun restoreBringsBackASavedSession() {
        store.saved = StoredSession("u7", "x@y.zz", "a", "r", now + 100, setOf(SyncConsent.BOOKMARKS))
        repo.restore()
        assertEquals(Session.SignedIn("u7", setOf(SyncConsent.BOOKMARKS)), sessions.session.value)
    }

    @Test
    fun freshTokenIsUsedWithoutRefreshing() = runTest {
        repo.verifyCode("a@b.co", "123456")
        assertEquals(AppResult.Success("a"), repo.validAccessToken())
        assertTrue(server.calls.none { it.startsWith("/auth/v1/token") })
    }

    @Test
    fun expiringTokenIsRefreshedAndSaved() = runTest {
        repo.verifyCode("a@b.co", "123456")
        now += 3600 - 30
        assertEquals(AppResult.Success("a2"), repo.validAccessToken())
        assertEquals("a2", store.saved!!.accessToken)
    }

    @Test
    fun refusedRefreshSignsOut() = runTest {
        repo.verifyCode("a@b.co", "123456")
        server.refreshAnswer = AppResult.Failure(AppError.Unauthenticated(AppError.REFRESH_REUSED))
        now += 7200
        assertTrue(repo.validAccessToken() is AppResult.Failure)
        assertEquals(Session.Guest, sessions.session.value)
        assertNull(store.saved)
    }

    @Test
    fun offlineRefreshKeepsTheSession() = runTest {
        repo.verifyCode("a@b.co", "123456")
        server.online = false
        now += 7200
        assertEquals(AppResult.Failure(AppError.Offline), repo.validAccessToken())
        assertTrue("an offline device must not lose its account", sessions.session.value is Session.SignedIn)
    }

    @Test
    fun signOutAlwaysClearsTheDeviceEvenOffline() = runTest {
        repo.verifyCode("a@b.co", "123456")
        server.online = false
        repo.signOut()
        assertEquals(Session.Guest, sessions.session.value)
        assertNull(store.saved)
    }
}
