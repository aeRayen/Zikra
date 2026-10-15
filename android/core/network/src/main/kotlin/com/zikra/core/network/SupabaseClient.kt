package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.common.result.flatMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Public client configuration (URL + publishable key). Both are public by design; RLS protects data. */
data class SupabaseConfig(val url: String, val publishableKey: String) {
    /** HTTPS is mandatory; plain HTTP only for the local machine (local Supabase stack, tests). */
    val isConfigured: Boolean
        get() = publishableKey.isNotBlank() &&
            (url.startsWith("https://") || LOCAL_HTTP.any { url.startsWith(it) })

    private companion object {
        val LOCAL_HTTP = listOf("http://127.0.0.1", "http://localhost")
    }
}

/** Tokens returned by Supabase Auth. Stored encrypted on device (WP 1.12); never logged. */
@Serializable
data class AuthTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long,
    val user: AuthUser,
)

@Serializable
data class AuthUser(val id: String)

/**
 * Thin client for the Zikra Supabase contract (ADR-024): Auth endpoints and the versioned `api.*_v1`
 * RPC functions. Every call goes through [transport], which in production is a [GatedTransport],
 * so the session/consent rules apply before any byte is sent.
 */
class SupabaseClient(private val config: SupabaseConfig, private val transport: Transport) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Sends a one-time code to [email] (creates the account on first use). */
    suspend fun requestEmailCode(email: String): AppResult<Unit> =
        post(
            path = "/auth/v1/otp",
            purpose = NetworkPurpose.SignIn,
            body = buildJsonObject {
                put("email", email)
                put("create_user", true)
            },
        ).flatMap { AppResult.Success(Unit) }

    /** Exchanges the code from the e-mail for a session. */
    suspend fun verifyEmailCode(email: String, code: String): AppResult<AuthTokens> =
        post(
            path = "/auth/v1/verify",
            purpose = NetworkPurpose.SignIn,
            body = buildJsonObject {
                put("type", "email")
                put("email", email)
                put("token", code)
            },
        ).flatMap { decode(AuthTokens.serializer(), it) }

    suspend fun refresh(refreshToken: String): AppResult<AuthTokens> =
        post(
            path = "/auth/v1/token?grant_type=refresh_token",
            purpose = NetworkPurpose.Account,
            body = buildJsonObject { put("refresh_token", refreshToken) },
        ).flatMap { decode(AuthTokens.serializer(), it) }

    suspend fun signOut(accessToken: String): AppResult<Unit> =
        post("/auth/v1/logout", NetworkPurpose.Account, JsonObject(emptyMap()), accessToken)
            .flatMap { AppResult.Success(Unit) }

    /** Calls `api.<function>` (must be a versioned contract function, e.g. `get_me_v1`). */
    suspend fun rpc(
        function: String,
        args: JsonObject,
        accessToken: String,
        purpose: NetworkPurpose = NetworkPurpose.Account,
    ): AppResult<JsonElement> {
        require(VERSIONED.matches(function)) { "Only versioned contract functions may be called: $function" }
        return post("/rest/v1/rpc/$function", purpose, args, accessToken).flatMap { bytes ->
            runCatching { json.parseToJsonElement(bytes.decodeToString()) }
                .fold({ AppResult.Success(it) }, { AppResult.Failure(AppError.Unexpected()) })
        }
    }

    suspend fun getMe(accessToken: String) = rpc("get_me_v1", JsonObject(emptyMap()), accessToken)

    suspend fun setConsent(accessToken: String, consent: SyncConsent, granted: Boolean, policyVersion: String) =
        rpc(
            function = "set_consent_v1",
            args = buildJsonObject {
                put("p_category", consent.wireName)
                put("p_granted", granted)
                put("p_policy_version", policyVersion)
            },
            accessToken = accessToken,
        )

    private suspend fun post(
        path: String,
        purpose: NetworkPurpose,
        body: JsonObject,
        accessToken: String? = null,
    ): AppResult<ByteArray> {
        if (!config.isConfigured) return AppResult.Failure(AppError.ServerUnavailable)
        val headers = buildMap {
            put("apikey", config.publishableKey)
            put("Content-Type", "application/json")
            if (accessToken != null) put("Authorization", "Bearer $accessToken")
        }
        return transport.send(
            NetworkRequest(purpose, "POST", config.url.trimEnd('/') + path, body.toString().toByteArray(), headers),
        )
    }

    private fun <T> decode(serializer: kotlinx.serialization.KSerializer<T>, bytes: ByteArray): AppResult<T> =
        runCatching { json.decodeFromString(serializer, bytes.decodeToString()) }
            .fold({ AppResult.Success(it) }, { AppResult.Failure(AppError.Unexpected()) })

    private companion object {
        val VERSIONED = Regex("^[a-z_]+_v[0-9]+$")
    }
}
