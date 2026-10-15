package com.zikra.core.network

import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult

/** Why a request is made. Every network call declares one; there is no "generic" purpose. */
sealed interface NetworkPurpose {
    /** Public, anonymous content (signed packs from the CDN). Allowed for everyone; carries no user data. */
    data object PublicContent : NetworkPurpose

    /** Starting a sign-in (request / verify a code, exchange a Google token). Explicit user action. */
    data object SignIn : NetworkPurpose

    /** Session refresh, sign-out, profile, consent, account deletion/export. Requires an account. */
    data object Account : NetworkPurpose

    /** Push/pull of the user's own data in one consent category. */
    data class UserDataSync(val category: SyncConsent) : NetworkPurpose
}

/**
 * The single decision point for network access (WP 1.13). Guests never reach account or sync
 * endpoints (they may only start a sign-in); signed-in users sync only categories they consented
 * to. Recovery and protection data have no purpose here at all: they cannot leave the device (ADR-011).
 */
class NetworkGate(private val sessions: SessionHolder) {

    fun check(purpose: NetworkPurpose): AppError? {
        val session = sessions.session.value
        return when (purpose) {
            NetworkPurpose.PublicContent, NetworkPurpose.SignIn -> null
            NetworkPurpose.Account -> if (session is Session.SignedIn) null else NOT_SIGNED_IN
            is NetworkPurpose.UserDataSync -> when (session) {
                Session.Guest -> NOT_SIGNED_IN
                is Session.SignedIn ->
                    if (purpose.category in session.grantedConsents) null else AppError.ConsentRequired
            }
        }
    }

    private companion object {
        val NOT_SIGNED_IN = AppError.Unauthenticated(AppError.NOT_SIGNED_IN)
    }
}

/** Minimal request abstraction; [OkHttpTransport] is the production implementation. */
fun interface Transport {
    suspend fun send(request: NetworkRequest): AppResult<ByteArray>
}

class NetworkRequest(
    val purpose: NetworkPurpose,
    val method: String,
    val url: String,
    val body: ByteArray? = null,
    val headers: Map<String, String> = emptyMap(),
)

/**
 * Wraps the real transport: a request the gate refuses fails immediately with an [AppError] and
 * never reaches [delegate], so no byte leaves the device.
 */
class GatedTransport(private val gate: NetworkGate, private val delegate: Transport) : Transport {
    override suspend fun send(request: NetworkRequest): AppResult<ByteArray> {
        val refusal = gate.check(request.purpose)
        return if (refusal != null) AppResult.Failure(refusal) else delegate.send(request)
    }
}
