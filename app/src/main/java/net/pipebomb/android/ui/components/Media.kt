package net.pipebomb.android.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun AlbumArtFallback(modifier: Modifier = Modifier) {
    val colors = PipeBombTheme.colors
    Box(
        modifier = modifier
            .clip(PipeBombTheme.shapes.small)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF323232), Color(0xFF242424)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "♪",
            style = PipeBombTheme.typography.greeting,
            color = colors.accentPrimary.copy(alpha = 0.3f),
        )
    }
}

@Composable
fun PipeBombProgressTrack(
    value: Float,
    max: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = PipeBombTheme.colors
    val safeMax = max.coerceAtLeast(0f)
    var widthPx by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val thumbRadius = with(LocalDensity.current) { 6.dp.toPx() }
    val loadingSweep = if (loading) {
        val transition = rememberInfiniteTransition(label = "progressLoading")
        val sweep by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 1_000,
                    easing = CubicBezierEasing(0.77f, 0f, 0.36f, 0.99f),
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "progressLoadingSweep",
        )
        sweep
    } else {
        0f
    }

    fun seekAt(x: Float) {
        if (!enabled || safeMax <= 0f) return
        onSeek((x / widthPx).coerceIn(0f, 1f) * safeMax)
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = value.coerceIn(0f, safeMax),
                    range = 0f..safeMax,
                )
                if (enabled) {
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, safeMax))
                        true
                    }
                }
            }
            .pointerInput(enabled, safeMax) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (!enabled || safeMax <= 0f) return@awaitEachGesture
                    widthPx = size.width.toFloat().coerceAtLeast(1f)
                    dragging = true
                    seekAt(down.position.x)
                    down.consume()
                    var change: PointerInputChange? = down
                    while (change?.pressed == true) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id }
                        change?.let {
                            seekAt(it.position.x)
                            it.consume()
                        }
                    }
                    dragging = false
                }
            },
    ) {
        val fraction = if (safeMax > 0f) (value / safeMax).coerceIn(0f, 1f) else 0f
        Canvas(Modifier.fillMaxWidth().height(4.dp).align(Alignment.Center)) {
            val radius = size.height / 2f
            drawRoundRect(
                color = colors.playerProgressTrack,
                cornerRadius = CornerRadius(radius, radius),
            )
            if (loading && !dragging) {
                val barWidth = size.width * 0.5f
                val left = loadingSweep * (size.width - barWidth)
                clipRect(0f, 0f, size.width, size.height) {
                    drawRoundRect(
                        color = colors.playerProgressBuffered,
                        topLeft = Offset(left, 0f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            } else if (fraction > 0f) {
                drawRoundRect(
                    color = colors.playerProgressPlayed,
                    size = androidx.compose.ui.geometry.Size(size.width * fraction, size.height),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
        if (enabled && safeMax > 0f && !(loading && !dragging)) {
            Canvas(Modifier.fillMaxSize()) {
                val x = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)
                drawCircle(
                    color = colors.playerProgressPlayed,
                    radius = if (dragging) thumbRadius * 1.15f else thumbRadius,
                    center = Offset(x, size.height / 2f),
                )
            }
        }
    }
}

@Composable
fun PlayPauseGlyph(playing: Boolean, modifier: Modifier = Modifier, color: Color = PipeBombTheme.colors.fgPrimary) {
    Box(modifier, contentAlignment = Alignment.Center) {
        if (playing) {
            androidx.compose.foundation.layout.Row {
                Box(Modifier.size(width = 3.dp, height = 14.dp).background(color))
                androidx.compose.foundation.layout.Spacer(Modifier.size(3.dp))
                Box(Modifier.size(width = 3.dp, height = 14.dp).background(color))
            }
        } else {
            Canvas(Modifier.size(14.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, color)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun MediaPreview() {
    PipeBombTheme {
        Box(Modifier.size(96.dp)) {
            AlbumArtFallback(Modifier.fillMaxSize())
        }
    }
}
