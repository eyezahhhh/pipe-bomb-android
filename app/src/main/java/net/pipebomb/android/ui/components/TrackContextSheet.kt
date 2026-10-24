package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackContextSheet(
    track: Track?,
    onDismiss: () -> Unit,
    onPlay: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onToggleSaved: (Track, Boolean) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (String) -> Unit,
) {
    if (track == null) return
    val colors = PipeBombTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val artistId = track.artists?.firstOrNull()?.artist?.let { it.uuid ?: it.identityKey }
    val albumId = track.albums?.firstOrNull()?.let { it.uuid ?: it.identityKey }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElevated,
        contentColor = colors.fgPrimary,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        dragHandle = null,
    ) {
        Column(modifier = Modifier.padding(bottom = PipeBombTheme.spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PipeBombTheme.spacing.lg, vertical = PipeBombTheme.spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
            ) {
                ResourceImage(
                    resource = DisplayText.artwork(track),
                    modifier = Modifier.size(48.dp).clip(PipeBombTheme.shapes.small),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = DisplayText.title(track),
                        style = PipeBombTheme.typography.trackTitle,
                        color = colors.fgPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = DisplayText.artists(track),
                        style = PipeBombTheme.typography.metadata,
                        color = colors.fgSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            HorizontalDivider(color = colors.borderStrong)
            SheetAction(
                icon = R.drawable.ic_tabler_play_filled,
                label = "Play now",
                onClick = {
                    onDismiss()
                    onPlay(track)
                },
            )
            SheetAction(
                icon = R.drawable.ic_tabler_list,
                label = "Play next",
                onClick = {
                    onDismiss()
                    onPlayNext(track)
                },
            )
            SheetAction(
                icon = R.drawable.ic_tabler_plus,
                label = "Add to queue",
                onClick = {
                    onDismiss()
                    onAddToQueue(track)
                },
            )
            SheetAction(
                icon = if (track.bookmarked == true) {
                    R.drawable.ic_tabler_bookmark_filled
                } else {
                    R.drawable.ic_tabler_bookmark
                },
                label = if (track.bookmarked == true) "Remove from saved" else "Save",
                onClick = {
                    onDismiss()
                    onToggleSaved(track, track.bookmarked != true)
                },
            )
            if (artistId != null) {
                SheetAction(
                    icon = R.drawable.ic_tabler_user,
                    label = "Go to artist",
                    onClick = {
                        onDismiss()
                        onOpenArtist(artistId)
                    },
                )
            }
            if (albumId != null) {
                SheetAction(
                    icon = R.drawable.ic_tabler_music,
                    label = "Go to album",
                    onClick = {
                        onDismiss()
                        onOpenAlbum(albumId)
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetAction(icon: Int, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = PipeBombTheme.spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(icon),
            contentDescription = null,
            tint = PipeBombTheme.colors.fgSecondary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = label,
            style = PipeBombTheme.typography.body,
            color = PipeBombTheme.colors.fgPrimary,
        )
    }
}
