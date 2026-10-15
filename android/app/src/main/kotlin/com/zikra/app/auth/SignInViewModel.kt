package com.zikra.app.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zikra.core.common.result.AppError
import com.zikra.core.common.result.AppResult
import com.zikra.core.network.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Two steps: enter e-mail → enter the 6-digit code from the e-mail. */
class SignInViewModel(private val auth: AuthRepository) : ViewModel() {

    enum class Step { Email, Code, Done }

    data class UiState(
        val email: String = "",
        val code: String = "",
        val step: Step = Step.Email,
        val busy: Boolean = false,
        val error: AppError? = null,
    )

    private val state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = state.asStateFlow()

    fun onEmailChange(value: String) = state.update { it.copy(email = value, error = null) }

    fun onCodeChange(value: String) =
        state.update { it.copy(code = value.filter(Char::isDigit).take(CODE_LENGTH), error = null) }

    fun sendCode() = run(Step.Code) { auth.requestCode(state.value.email) }

    fun verify() = run(Step.Done) { auth.verifyCode(state.value.email, state.value.code) }

    fun changeEmail() = state.update { it.copy(step = Step.Email, code = "", error = null) }

    private fun run(next: Step, call: suspend () -> AppResult<Unit>) {
        if (state.value.busy) return
        state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            when (val result = call()) {
                is AppResult.Success -> state.update { it.copy(busy = false, step = next) }
                is AppResult.Failure -> state.update { it.copy(busy = false, error = result.error) }
            }
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}
