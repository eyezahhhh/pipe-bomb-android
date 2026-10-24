package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun PipeBombSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    color: Color = PipeBombTheme.colors.accentPrimary,
    strokeWidth: Dp = 4.dp,
) {
    CircularProgressIndicator(
        modifier = modifier.size(size),
        color = color,
        strokeWidth = strokeWidth,
    )
}

@Composable
fun PipeBombNotification(message: String, modifier: Modifier = Modifier) {
    val colors = PipeBombTheme.colors
    Box(
        modifier = modifier
            .width(240.dp)
            .background(colors.bgInverse, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = message,
            style = PipeBombTheme.typography.bodySecondary,
            color = colors.fgInverse,
        )
    }
}

@Composable
fun PipeBombSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        val colors = PipeBombTheme.colors
        Snackbar(
            snackbarData = data,
            containerColor = colors.bgInverse,
            contentColor = colors.fgInverse,
            actionColor = colors.accentPrimary,
            shape = RoundedCornerShape(4.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun FeedbackPreview() {
    PipeBombTheme {
        Box(Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            PipeBombNotification("Playback failed")
        }
    }
}
