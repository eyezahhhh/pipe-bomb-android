package net.pipebomb.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.MainNavigation
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.feature.auth.LoginScreen
import net.pipebomb.android.feature.connect.ConnectScreen
import net.pipebomb.android.ui.components.PipeBombSpinner

@Composable
fun PipeBombApp(viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        RootState.Loading -> LoadingScreen()
        is RootState.Connect -> ConnectScreen(prefill = current.prefill)
        RootState.Login -> LoginScreen()
        RootState.Ready -> MainNavigation()
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PipeBombTheme.colors.bgBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
        ) {
            Image(
                painter = painterResource(R.drawable.pipebomb_logo),
                contentDescription = "Pipe Bomb",
                modifier = Modifier.size(72.dp),
            )
            PipeBombSpinner(size = 24.dp, strokeWidth = 3.dp)
        }
    }
}
