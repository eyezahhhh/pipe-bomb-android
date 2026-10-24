package net.pipebomb.android.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonStyle
import net.pipebomb.android.ui.components.ResourceImage

@Composable
fun MiniPlayer(
    track: Track,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PipeBombTheme.spacing.sm)
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .clickable(onClick = onOpen)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(track),
            modifier = Modifier.size(48.dp).clip(PipeBombTheme.shapes.small),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = DisplayText.title(track),
                style = PipeBombTheme.typography.trackTitle,
                color = PipeBombTheme.colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DisplayText.artists(track),
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PipeBombIconButton(
            icon = ImageVector.vectorResource(
                if (isPlaying) R.drawable.ic_tabler_pause_filled else R.drawable.ic_tabler_play_filled,
            ),
            contentDescription = if (isPlaying) "Pause" else "Play",
            onClick = onToggle,
            style = PipeBombIconButtonStyle.Background,
        )
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_skip_forward_filled),
            contentDescription = "Next",
            onClick = onNext,
            size = PipeBombIconButtonSize.Medium,
        )
    }
}
