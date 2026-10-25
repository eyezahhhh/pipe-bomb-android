package net.pipebomb.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun MediaActions(
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    playLoading: Boolean = false,
    onShuffle: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToEnd: (() -> Unit)? = null,
    isSaved: Boolean? = null,
    onToggleSave: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_play_filled),
            contentDescription = "Play",
            onClick = onPlay,
            size = PipeBombIconButtonSize.ExtraLarge,
            style = PipeBombIconButtonStyle.Background,
            enabled = enabled,
            loading = playLoading,
        )
        onShuffle?.let {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_shuffle),
                contentDescription = "Shuffle",
                onClick = it,
                size = PipeBombIconButtonSize.Medium,
                tone = PipeBombIconButtonTone.Neutral,
                enabled = enabled,
            )
        }
        onPlayNext?.let {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_list_start),
                contentDescription = "Play next",
                onClick = it,
                size = PipeBombIconButtonSize.Medium,
                tone = PipeBombIconButtonTone.Neutral,
                enabled = enabled,
            )
        }
        onAddToEnd?.let {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_lucide_list_end),
                contentDescription = "Add to queue",
                onClick = it,
                size = PipeBombIconButtonSize.Medium,
                tone = PipeBombIconButtonTone.Neutral,
                enabled = enabled,
            )
        }
        if (onToggleSave != null) {
            PipeBombIconButton(
                icon = ImageVector.vectorResource(
                    if (isSaved == true) {
                        R.drawable.ic_tabler_bookmark_filled
                    } else {
                        R.drawable.ic_tabler_bookmark
                    },
                ),
                contentDescription = if (isSaved == true) "Remove from saved" else "Save",
                onClick = onToggleSave,
                size = PipeBombIconButtonSize.Medium,
                tone = PipeBombIconButtonTone.Primary,
                enabled = enabled,
            )
        }
        trailing()
    }
}
