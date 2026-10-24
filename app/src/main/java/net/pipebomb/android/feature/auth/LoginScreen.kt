package net.pipebomb.android.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombTextField

@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AuthContent(
        state = state,
        onUsernameChange = viewModel::setUsername,
        onPasswordChange = viewModel::setPassword,
        onSubmit = viewModel::submit,
        onToggleMode = viewModel::toggleMode,
        onChangeServer = viewModel::changeServer,
        modifier = modifier,
    )
}

@Composable
private fun AuthContent(
    state: AuthUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onToggleMode: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = state.mode == AuthMode.Login
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PipeBombTheme.colors.bgBase)
            .padding(PipeBombTheme.spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.pipebomb_logo),
                contentDescription = "Pipe Bomb",
                modifier = Modifier.size(64.dp),
            )
            Text(
                text = if (isLogin) "Log in" else "Create account",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (isLogin) {
                    "Use your Pipe Bomb account to continue."
                } else {
                    "Create an account on this server."
                },
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.fgTertiary,
                textAlign = TextAlign.Center,
            )

            PipeBombTextField(
                value = state.username,
                onValueChange = onUsernameChange,
                placeholder = "Username",
                enabled = !state.isSubmitting,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                ),
            )
            PipeBombTextField(
                value = state.password,
                onValueChange = onPasswordChange,
                placeholder = "Password",
                password = true,
                enabled = !state.isSubmitting,
                isError = state.error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            )

            state.error?.let { error ->
                Text(
                    text = error,
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.error,
                )
            }

            PipeBombButton(
                text = if (isLogin) "Log in" else "Create account",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.username.isNotBlank() && state.password.isNotBlank(),
                loading = state.isSubmitting,
            )
            PipeBombButton(
                text = if (isLogin) "Create an account instead" else "I already have an account",
                onClick = onToggleMode,
                variant = PipeBombButtonVariant.Ghost,
                enabled = !state.isSubmitting,
            )
            PipeBombButton(
                text = "Change server",
                onClick = onChangeServer,
                variant = PipeBombButtonVariant.Ghost,
                enabled = !state.isSubmitting,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 760)
@Composable
private fun LoginScreenPreview() {
    PipeBombTheme {
        AuthContent(
            state = AuthUiState(username = "listener"),
            onUsernameChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onToggleMode = {},
            onChangeServer = {},
        )
    }
}
