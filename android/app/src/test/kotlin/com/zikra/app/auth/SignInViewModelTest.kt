package com.zikra.app.auth

import com.zikra.app.R
import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.network.AuthRepository
import com.zikra.core.network.GatedTransport
import com.zikra.core.network.NetworkGate
import com.zikra.core.network.NetworkRequest
import com.zikra.core.network.Session
import com.zikra.core.network.SessionHolder
import com.zikra.core.network.StoredSession
import com.zikra.core.network.SupabaseClient
import com.zikra.core.network.SupabaseConfig
import com.zikra.core.network.TokenStore
import com.zikra.core.network.Transport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SignInViewModelTest {

    private val sessions = SessionHolder()
    private var verifyAnswer: AppResult<ByteArray> =
        AppResult.Success(
            """{"access_token":"a","refresh_token":"r","expires_in":3600,"user":{"id":"u1"}}""".toByteArray()
        )

    private val transport = Transport { request: NetworkRequest ->
        if (request.url.endsWith("/auth/v1/verify")) verifyAnswer else AppResult.Success("{}".toByteArray())
    }
    private val store = object : TokenStore {
        var saved: StoredSession? = null
        override fun load() = saved
        override fun save(session: StoredSession) {
            saved = session
        }
        override fun clear() {
            saved = null
        }
    }
    private val auth = AuthRepository(
        SupabaseClient(
            SupabaseConfig("https://example.invalid", "k"),
            GatedTransport(NetworkGate(sessions), transport)
        ),
        store,
        sessions,
    )

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun fullFlowSignsIn() {
        val vm = SignInViewModel(auth)
        vm.onEmailChange("me@example.com")
        vm.sendCode()
        assertEquals(SignInViewModel.Step.Code, vm.uiState.value.step)

        vm.onCodeChange("12 34-56xyz")
        assertEquals("only digits, max 6", "123456", vm.uiState.value.code)
        vm.verify()
        assertEquals(SignInViewModel.Step.Done, vm.uiState.value.step)
        assertEquals(Session.SignedIn("u1", emptySet()), sessions.session.value)
    }

    @Test
    fun invalidEmailStaysOnTheEmailStepWithAnError() {
        val vm = SignInViewModel(auth)
        vm.onEmailChange("nope")
        vm.sendCode()
        assertEquals(SignInViewModel.Step.Email, vm.uiState.value.step)
        assertEquals(AppError.Validation(listOf("email")), vm.uiState.value.error)
        assertEquals(R.string.error_invalid_email, errorMessage(vm.uiState.value.error!!, vm.uiState.value.step))
    }

    @Test
    fun wrongCodeShowsWrongCodeMessage() {
        verifyAnswer = AppResult.Failure(AppError.Unauthenticated(AppError.NOT_SIGNED_IN))
        val vm = SignInViewModel(auth)
        vm.onEmailChange("me@example.com")
        vm.sendCode()
        vm.onCodeChange("000000")
        vm.verify()
        assertEquals(SignInViewModel.Step.Code, vm.uiState.value.step)
        assertEquals(R.string.error_wrong_code, errorMessage(vm.uiState.value.error!!, vm.uiState.value.step))
        assertEquals(Session.Guest, sessions.session.value)
    }

    @Test
    fun offlineIsReportedAsOffline() {
        assertEquals(R.string.error_offline, errorMessage(AppError.Offline, SignInViewModel.Step.Email))
    }
}
