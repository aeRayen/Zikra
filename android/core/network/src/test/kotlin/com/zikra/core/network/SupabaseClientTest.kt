package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** WP 1.8: the client speaks the contract correctly and every request still goes through the gate. */
class SupabaseClientTest {

    private val server = MockWebServer()
    private val sessions = SessionHolder()
    private lateinit var client: SupabaseClient

    @Before
    fun setUp() {
        server.start()
        val config = SupabaseConfig(server.url("/").toString().trimEnd('/'), "sb_publishable_test")
        client = SupabaseClient(config, GatedTransport(NetworkGate(sessions), OkHttpTransport()))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun plainHttpIsRefusedExceptForTheLocalMachine() {
        assertTrue(SupabaseConfig("https://x.supabase.co", "k").isConfigured)
        assertTrue(SupabaseConfig("http://127.0.0.1:54321", "k").isConfigured)
        assertTrue(!SupabaseConfig("http://x.supabase.co", "k").isConfigured)
        assertTrue(!SupabaseConfig("https://x.supabase.co", "").isConfigured)
    }

    @Test
    fun rpcSendsKeyTokenAndArgsToTheVersionedFunction() = runTest {
        sessions.update(Session.SignedIn("u1", emptySet()))
        server.enqueue(MockResponse().setBody("""{"id":"u1","displayName":null}"""))

        val result = client.getMe(accessToken = "user-jwt")

        val request = server.takeRequest()
        assertEquals("/rest/v1/rpc/get_me_v1", request.path)
        assertEquals("sb_publishable_test", request.getHeader("apikey"))
        assertEquals("Bearer user-jwt", request.getHeader("Authorization"))
        val body = (result as AppResult.Success).value.jsonObject
        assertEquals("u1", body["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun guestIsStoppedBeforeTheNetwork() = runTest {
        val result = client.getMe(accessToken = "stale-token")
        assertEquals(AppResult.Failure(AppError.Unauthenticated(AppError.NOT_SIGNED_IN)), result)
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test
    fun guestCanRequestASignInCodeWithoutAUserToken() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(client.requestEmailCode("a@example.com") is AppResult.Success)
        val request = server.takeRequest()
        assertEquals("/auth/v1/otp", request.path)
        assertNull("no Authorization header before sign-in", request.getHeader("Authorization"))
        assertTrue(request.body.readUtf8().contains("\"create_user\":true"))
    }

    @Test
    fun verifyParsesTheSession() = runTest {
        val session = """{"access_token":"a","refresh_token":"r","expires_in":3600,""" +
            """"token_type":"bearer","user":{"id":"u9","email":"x"}}"""
        server.enqueue(MockResponse().setBody(session))
        val tokens = (client.verifyEmailCode("a@example.com", "123456") as AppResult.Success).value
        assertEquals("u9", tokens.user.id)
        assertEquals(3600, tokens.expiresInSeconds)
    }

    @Test
    fun databaseErrorCodesBecomeStableAppErrors() = runTest {
        sessions.update(Session.SignedIn("u1", setOf(SyncConsent.BOOKMARKS)))
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"code":"42501","message":"sync.consent_required"}"""),
        )
        val result = client.rpc("sync_pull_v1", kotlinx.serialization.json.JsonObject(emptyMap()), "t")
        assertEquals(AppResult.Failure(AppError.ConsentRequired), result)
    }

    @Test
    fun httpStatusesMapToAppErrors() {
        val expired = AppError.Unauthenticated(AppError.TOKEN_EXPIRED)
        assertEquals(expired, OkHttpTransport.errorFor(401, "{}".toByteArray()))
        assertEquals(AppError.RateLimited, OkHttpTransport.errorFor(429, ByteArray(0)))
        assertEquals(AppError.ServerUnavailable, OkHttpTransport.errorFor(503, "<html>".toByteArray()))
        assertEquals(AppError.Validation(), OkHttpTransport.errorFor(400, """{"message":"bad"}""".toByteArray()))
    }

    @Test
    fun unreachableServerIsOffline() = runTest {
        sessions.update(Session.SignedIn("u1", emptySet()))
        server.shutdown()
        assertEquals(AppResult.Failure(AppError.Offline), client.getMe("t"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun onlyVersionedContractFunctionsCanBeCalled() = runTest {
        client.rpc("delete_everything", kotlinx.serialization.json.JsonObject(emptyMap()), "t")
    }
}
