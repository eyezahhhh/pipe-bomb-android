package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
) {
    val colors = PipeBombTheme.colors
    Slider(
        value = value.coerceIn(0f, max.coerceAtLeast(0f)),
        onValueChange = onSeek,
        modifier = modifier.height(32.dp),
        enabled = enabled && max > 0f,
        valueRange = 0f..max.coerceAtLeast(1f),
        colors = SliderDefaults.colors(
            thumbColor = colors.sliderThumb,
            activeTrackColor = colors.sliderFill,
            inactiveTrackColor = colors.playerProgressTrack,
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
        ),
    )
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
            androidx.compose.foundation.Canvas(Modifier.size(14.dp)) {
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
