package net.pipebomb.android.feature.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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
fun ConnectScreen(
    prefill: String?,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var input by rememberSaveable(prefill) { mutableStateOf(prefill.orEmpty()) }

    ConnectContent(
        input = input,
        state = state,
        onInputChange = { input = it },
        onConnect = { viewModel.connect(input) },
        onOpenServer = { state.setupUrl?.let { uriHandler.openUri(it) } },
        onRetry = { viewModel.retry() },
        modifier = modifier,
    )
}

@Composable
private fun ConnectContent(
    input: String,
    state: ConnectUiState,
    onInputChange: (String) -> Unit,
    onConnect: () -> Unit,
    onOpenServer: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = "Connect to your server",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Enter the address of your self-hosted Pipe Bomb server.",
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.fgTertiary,
                textAlign = TextAlign.Center,
            )

            PipeBombTextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = "https://music.example.com",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                isError = state.error != null,
            )

            if (input.trim().startsWith("http://", ignoreCase = true)) {
                Text(
                    text = "This connection is not encrypted. Only use HTTP on a trusted local network.",
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.warning,
                )
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.error,
                )
            }

            if (state.needsSetup) {
                SetupCard(
                    onOpen = onOpenServer,
                    onRetry = onRetry,
                    connecting = state.isConnecting,
                )
            } else {
                PipeBombButton(
                    text = "Connect",
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = input.isNotBlank(),
                    loading = state.isConnecting,
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    connecting: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PipeBombTheme.colors.bgSurface, PipeBombTheme.shapes.medium)
            .padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        Text(
            text = "Server setup required",
            style = PipeBombTheme.typography.trackTitle,
            color = PipeBombTheme.colors.fgPrimary,
        )
        Text(
            text = "Finish setting up Pipe Bomb in your browser, then retry.",
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
            PipeBombButton(
                text = "Open server",
                onClick = onOpen,
                variant = PipeBombButtonVariant.Secondary,
            )
            PipeBombButton(
                text = "Retry",
                onClick = onRetry,
                loading = connecting,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, heightDp = 720)
@Composable
private fun ConnectScreenPreview() {
    PipeBombTheme {
        ConnectContent(
            input = "http://192.168.1.20:3000",
            state = ConnectUiState(),
            onInputChange = {},
            onConnect = {},
            onOpenServer = {},
            onRetry = {},
        )
    }
}
