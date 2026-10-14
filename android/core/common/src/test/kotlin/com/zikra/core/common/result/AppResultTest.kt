package com.zikra.core.common.result

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AppResultTest {

    @Test
    fun mapAndFlatMapTransformSuccessOnly() {
        val ok: AppResult<Int> = AppResult.Success(2)
        assertEquals(AppResult.Success(4), ok.map { it * 2 })
        assertEquals(AppResult.Success("2"), ok.flatMap { AppResult.Success(it.toString()) })

        val failed: AppResult<Int> = AppResult.Failure(AppError.Offline)
        assertEquals(failed, failed.map { it * 2 })
        assertNull(failed.getOrNull())
        assertEquals(AppError.Offline, failed.errorOrNull())
    }

    @Test
    fun runCatchingConvertsExceptions() {
        val result = appRunCatching(mapError = { AppError.Storage }) { error("disk full") }
        assertEquals(AppResult.Failure(AppError.Storage), result)
    }

    @Test
    fun runCatchingRethrowsCancellation() {
        try {
            appRunCatching { throw CancellationException("cancelled") }
            fail("CancellationException must propagate")
        } catch (expected: CancellationException) {
            assertEquals("cancelled", expected.message)
        }
    }

    @Test
    fun everyDocumentedServerCodeMapsToAKnownError() {
        // Codes from SYNC_PROTOCOL_AR.md §8 that the client must understand.
        val documented = listOf(
            "auth.token_invalid", "auth.token_expired", "auth.refresh_reused", "auth.google_token_invalid",
            "request.validation_failed", "request.rate_limited", "sync.consent_required",
            "resource.not_found", "app.version_unsupported", "server.unavailable",
        )
        documented.forEach { code ->
            val error = AppError.fromCode(code)
            assertTrue("$code mapped to Unexpected", error !is AppError.Unexpected)
        }
        assertTrue(AppError.fromCode("something.new") is AppError.Unexpected)
    }

    @Test
    fun authCodesKeepTheirSpecificCode() {
        assertEquals("auth.refresh_reused", AppError.fromCode("auth.refresh_reused").code)
    }

    @Test
    fun onlyTransientErrorsAreRetryable() {
        assertTrue(AppError.Offline.isRetryable)
        assertTrue(AppError.ServerUnavailable.isRetryable)
        assertTrue(!AppError.NotFound.isRetryable)
        assertTrue(!AppError.Unauthenticated().isRetryable)
    }
}
