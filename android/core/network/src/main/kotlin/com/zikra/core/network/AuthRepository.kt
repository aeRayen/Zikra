package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.common.result.map
import kotlinx.serialization.Serializable

/** What is kept on the device after sign-in (encrypted by the [TokenStore] implementation). */
@Serializable
data class StoredSession(
    val userId: String,
    val email: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val grantedConsents: Set<SyncConsent> = emptySet(),
)

/** Device storage for the session. Android implementation: Keystore-encrypted file, not backed up. */
interface TokenStore {
    fun load(): StoredSession?

    fun save(session: StoredSession)

    fun clear()
}

fun interface Clock {
    fun nowEpochSeconds(): Long
}

/**
 * Optional account (ADR-024). Signing in is an explicit user action; signing out always succeeds
 * locally, even offline (the server session then simply expires). Never logs tokens or e-mail.
 */
class AuthRepository(
    private val client: SupabaseClient,
    private val store: TokenStore,
    private val sessions: SessionHolder,
    private val clock: Clock = Clock { System.currentTimeMillis() / MILLIS },
) {

    /** Restores a saved session at app start; stays a guest when nothing is stored. */
    fun restore() {
        store.load()?.let { sessions.update(Session.SignedIn(it.userId, it.grantedConsents)) }
    }

    suspend fun requestCode(email: String): AppResult<Unit> {
        val normalized = email.trim().lowercase()
        if (!EMAIL.matches(normalized)) return AppResult.Failure(AppError.Validation(listOf("email")))
        return client.requestEmailCode(normalized)
    }

    suspend fun verifyCode(email: String, code: String): AppResult<Unit> {
        val digits = code.trim()
        if (!CODE.matches(digits)) return AppResult.Failure(AppError.Validation(listOf("code")))
        val normalized = email.trim().lowercase()
        return client.verifyEmailCode(normalized, digits).map { tokens ->
            val stored = StoredSession(
                userId = tokens.user.id,
                email = normalized,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                expiresAtEpochSeconds = clock.nowEpochSeconds() + tokens.expiresInSeconds,
            )
            store.save(stored)
            sessions.update(Session.SignedIn(stored.userId, stored.grantedConsents))
        }
    }

    /** A usable access token, refreshed when it expires within a minute. Signs out if refresh is refused. */
    suspend fun validAccessToken(): AppResult<String> {
        val current = store.load() ?: return AppResult.Failure(AppError.Unauthenticated(AppError.NOT_SIGNED_IN))
        val fresh = current.expiresAtEpochSeconds - clock.nowEpochSeconds() > REFRESH_MARGIN_SECONDS
        return if (fresh) AppResult.Success(current.accessToken) else refresh(current)
    }

    private suspend fun refresh(current: StoredSession): AppResult<String> =
        when (val refreshed = client.refresh(current.refreshToken)) {
            is AppResult.Success -> {
                val tokens = refreshed.value
                val updated = current.copy(
                    accessToken = tokens.accessToken,
                    refreshToken = tokens.refreshToken,
                    expiresAtEpochSeconds = clock.nowEpochSeconds() + tokens.expiresInSeconds,
                )
                store.save(updated)
                AppResult.Success(updated.accessToken)
            }
            is AppResult.Failure -> {
                if (refreshed.error is AppError.Unauthenticated) signOutLocally()
                refreshed
            }
        }

    fun currentEmail(): String? = store.load()?.email

    /** Best-effort server sign-out, then always clears the device session. */
    suspend fun signOut() {
        store.load()?.let { client.signOut(it.accessToken) }
        signOutLocally()
    }

    private fun signOutLocally() {
        store.clear()
        sessions.update(Session.Guest)
    }

    private companion object {
        const val MILLIS = 1000L
        const val REFRESH_MARGIN_SECONDS = 60L
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")
        val CODE = Regex("^[0-9]{6}$")
    }
}
