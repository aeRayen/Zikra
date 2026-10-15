package com.zikra.app.auth

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.zikra.app.R
import com.zikra.core.common.result.AppError
import com.zikra.core.designsystem.component.BannerKind
import com.zikra.core.designsystem.component.StatusBanner
import com.zikra.core.designsystem.component.ZikraPrimaryButton
import com.zikra.core.designsystem.component.ZikraSecondaryButton
import com.zikra.core.designsystem.theme.ZikraSpacing

/** «الحساب»: optional. A guest sees why an account exists and that everything works without it. */
@Composable
fun AccountScreen(
    signedInEmail: String?,
    accountsAvailable: Boolean,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ZikraSpacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(ZikraSpacing.lg),
    ) {
        Title(R.string.account_title)
        when {
            signedInEmail != null -> {
                Text(text = stringResource(R.string.account_signed_in_as, signedInEmail))
                ZikraSecondaryButton(text = stringResource(R.string.account_sign_out), onClick = onSignOut)
            }
            accountsAvailable -> {
                Text(text = stringResource(R.string.account_guest_explainer))
                ZikraPrimaryButton(text = stringResource(R.string.account_sign_in_email), onClick = onSignIn)
            }
            else -> Text(text = stringResource(R.string.account_unavailable))
        }
    }
}

@Composable
fun SignInScreen(
    state: SignInViewModel.UiState,
    onEmailChange: (String) -> Unit,
    onCodeChange: (String) -> Unit,
    onSendCode: () -> Unit,
    onVerify: () -> Unit,
    onChangeEmail: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ZikraSpacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(ZikraSpacing.lg),
    ) {
        Title(R.string.sign_in_title)
        state.error?.let { error ->
            StatusBanner(message = stringResource(errorMessage(error, state.step)), kind = BannerKind.Error)
        }
        if (state.step == SignInViewModel.Step.Email) {
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChange,
                label = { Text(text = stringResource(R.string.sign_in_email_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )
            ZikraPrimaryButton(
                text = stringResource(R.string.sign_in_send_code),
                onClick = onSendCode,
                loading = state.busy,
                loadingDescription = stringResource(R.string.loading),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Text(text = stringResource(R.string.sign_in_code_sent, state.email))
            OutlinedTextField(
                value = state.code,
                onValueChange = onCodeChange,
                label = { Text(text = stringResource(R.string.sign_in_code_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            ZikraPrimaryButton(
                text = stringResource(R.string.sign_in_verify),
                onClick = onVerify,
                loading = state.busy,
                loadingDescription = stringResource(R.string.loading),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onChangeEmail) { Text(text = stringResource(R.string.sign_in_change_email)) }
        }
        Text(
            text = stringResource(R.string.sign_in_privacy_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Title(@StringRes id: Int) {
    Text(
        text = stringResource(id),
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.semantics { heading() },
    )
}

/** Stable error → localized message (never raw server text). */
@StringRes
internal fun errorMessage(error: AppError, step: SignInViewModel.Step): Int = when {
    error is AppError.Validation && "email" in error.fields -> R.string.error_invalid_email
    error is AppError.Validation && "code" in error.fields -> R.string.error_invalid_code
    error is AppError.Offline -> R.string.error_offline
    error is AppError.RateLimited -> R.string.error_rate_limited
    step == SignInViewModel.Step.Code && (error is AppError.Unauthenticated || error is AppError.Validation) ->
        R.string.error_wrong_code
    else -> R.string.error_generic
}
