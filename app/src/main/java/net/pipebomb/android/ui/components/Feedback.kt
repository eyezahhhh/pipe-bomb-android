package net.pipebomb.android.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombPalette
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun PipeBombSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    accent: Boolean = false,
    strokeWidth: Dp = 5.dp,
) {
    val primary = if (accent) PipeBombPalette.Accent500 else PipeBombPalette.Gray700
    val secondary = if (accent) PipeBombPalette.Gray100 else PipeBombPalette.Gray900
    val transition = rememberInfiniteTransition(label = "spinner")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 700,
                easing = CubicBezierEasing(0.5f, 0.2f, 0.5f, 0.8f),
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    Canvas(modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2f
        val arcSize = androidx.compose.ui.geometry.Size(
            this.size.width - stroke,
            this.size.height - stroke,
        )
        val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
        val startAngle = rotation - 45f
        drawArc(
            color = secondary,
            startAngle = startAngle,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        drawArc(
            color = primary,
            startAngle = startAngle,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
        drawArc(
            color = primary,
            startAngle = startAngle + 180f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
    }
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
