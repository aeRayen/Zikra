package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * HTTP transport. Never logs bodies or headers (tokens, user data). Maps failures to stable
 * [AppError] codes; our database raises its own codes (e.g. `sync.consent_required`) as the message.
 */
class OkHttpTransport(
    private val client: OkHttpClient = defaultClient(),
) : Transport {

    override suspend fun send(request: NetworkRequest): AppResult<ByteArray> {
        val body = request.body?.toRequestBody(JSON)
        val httpRequest = Request.Builder()
            .url(request.url)
            .method(request.method, body ?: if (request.method == "POST") ByteArray(0).toRequestBody(JSON) else null)
            .apply { request.headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(httpRequest)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        continuation.resume(AppResult.Failure(AppError.Offline))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = response.use { r ->
                            val bytes = r.body?.bytes() ?: ByteArray(0)
                            if (r.isSuccessful) AppResult.Success(bytes) else AppResult.Failure(errorFor(r.code, bytes))
                        }
                        continuation.resume(result)
                    }
                },
            )
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        private val json = Json { ignoreUnknownKeys = true }
        private val STABLE_CODE = Regex("^[a-z]+(\\.[a-z_]+)+$")
        private const val TIMEOUT_SECONDS = 20L

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS * 2, TimeUnit.SECONDS)
            .build()

        /** HTTP status + body → stable error. Exposed for tests. */
        internal fun errorFor(status: Int, body: ByteArray): AppError {
            val message = runCatching {
                json.parseToJsonElement(body.decodeToString()) as? JsonObject
            }.getOrNull()?.get("message")?.jsonPrimitive?.content
            if (message != null && STABLE_CODE.matches(message)) {
                val mapped = AppError.fromCode(message)
                if (mapped !is AppError.Unexpected) return mapped
            }
            return when (status) {
                HTTP_UNAUTHORIZED -> AppError.Unauthenticated(AppError.TOKEN_EXPIRED)
                HTTP_FORBIDDEN -> AppError.Unauthenticated(AppError.NOT_SIGNED_IN)
                HTTP_BAD_REQUEST, HTTP_UNPROCESSABLE -> AppError.Validation()
                HTTP_NOT_FOUND -> AppError.NotFound
                HTTP_TOO_MANY -> AppError.RateLimited
                in HTTP_SERVER_ERRORS -> AppError.ServerUnavailable
                else -> AppError.Unexpected()
            }
        }

        private const val HTTP_BAD_REQUEST = 400
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_UNPROCESSABLE = 422
        private const val HTTP_TOO_MANY = 429
        private val HTTP_SERVER_ERRORS = 500..599
    }
}
