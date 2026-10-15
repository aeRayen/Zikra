package com.zikra.core.common.result

/**
 * Every failure the UI can show. [code] is stable and is the key the app uses to pick a
 * localized message (SYNC_PROTOCOL_AR.md §8, I18N_SPEC_AR.md §8). Never show [code] or a raw
 * exception message to the user.
 */
sealed interface AppError {
    val code: String

    /** No connectivity or the request timed out; usually retryable. */
    data object Offline : AppError {
        override val code = "network.offline"
    }

    data object ServerUnavailable : AppError {
        override val code = "server.unavailable"
    }

    data object RateLimited : AppError {
        override val code = "request.rate_limited"
    }

    /** The session is missing, expired, or was revoked; the user must sign in again. */
    data class Unauthenticated(override val code: String = AppError.TOKEN_INVALID) : AppError

    data object ConsentRequired : AppError {
        override val code = "sync.consent_required"
    }

    data object NotFound : AppError {
        override val code = "resource.not_found"
    }

    data object AppVersionUnsupported : AppError {
        override val code = "app.version_unsupported"
    }

    /** Input rejected by validation; [fields] are field names only, never values. */
    data class Validation(val fields: List<String> = emptyList()) : AppError {
        override val code = "request.validation_failed"
    }

    /** Local database or file failure. */
    data object Storage : AppError {
        override val code = "local.storage_failed"
    }

    /** A signed content pack failed verification (CONTENT_PACKS_AR.md §3). */
    data object ContentIntegrity : AppError {
        override val code = "content.integrity_failed"
    }

    /** Anything not classified. [cause] is for logging through the redacting logger only. */
    data class Unexpected(val cause: Throwable? = null) : AppError {
        override val code = "app.unexpected"
    }

    companion object {
        const val TOKEN_INVALID = "auth.token_invalid"
        const val TOKEN_EXPIRED = "auth.token_expired"
        const val REFRESH_REUSED = "auth.refresh_reused"
        const val NOT_SIGNED_IN = "auth.not_signed_in"

        private val AUTH_CODES = setOf(
            TOKEN_INVALID,
            TOKEN_EXPIRED,
            REFRESH_REUSED,
            NOT_SIGNED_IN,
            "auth.google_token_invalid",
        )

        /** Maps a stable server/backend code to an [AppError]. Unknown codes become [Unexpected]. */
        fun fromCode(code: String): AppError = when (code) {
            in AUTH_CODES -> Unauthenticated(code)
            Offline.code -> Offline
            ServerUnavailable.code -> ServerUnavailable
            RateLimited.code -> RateLimited
            ConsentRequired.code -> ConsentRequired
            NotFound.code -> NotFound
            AppVersionUnsupported.code -> AppVersionUnsupported
            "request.validation_failed" -> Validation()
            else -> Unexpected()
        }
    }
}

/** True when retrying the same operation later may succeed. */
val AppError.isRetryable: Boolean
    get() = this is AppError.Offline || this is AppError.ServerUnavailable || this is AppError.RateLimited
