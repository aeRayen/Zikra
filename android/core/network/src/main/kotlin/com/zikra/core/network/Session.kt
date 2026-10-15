package com.zikra.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Who is using the app. Everyone starts as [Guest]; an account is optional (ADR-024). */
sealed interface Session {
    /** No account. Never contacts the account/sync backend (ADR-025 §2). */
    data object Guest : Session

    /** Signed in. [grantedConsents] are the sync categories the user agreed to (SYNC_PROTOCOL §1). */
    data class SignedIn(val userId: String, val grantedConsents: Set<SyncConsent>) : Session
}

/** Consent categories, identical to the server's (api/openapi.yaml ConsentCategory). */
enum class SyncConsent(val wireName: String) {
    SETTINGS("sync.settings"),
    BOOKMARKS("sync.bookmarks"),
    READING("sync.reading"),
    WORSHIP("sync.worship"),
}

/** Holds the current session. The real implementation (persisted) arrives with sign-in, WP 1.12. */
class SessionHolder(initial: Session = Session.Guest) {
    private val state = MutableStateFlow(initial)
    val session: StateFlow<Session> = state.asStateFlow()

    fun update(session: Session) {
        state.value = session
    }
}
