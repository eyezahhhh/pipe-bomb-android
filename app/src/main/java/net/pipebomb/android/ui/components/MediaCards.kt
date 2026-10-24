package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Album
import net.pipebomb.android.core.model.Artist
import net.pipebomb.android.core.model.Playlist

private val CardWidth = 156.dp

@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(album),
            modifier = Modifier
                .size(CardWidth)
                .clip(PipeBombTheme.shapes.medium)
                .background(PipeBombTheme.colors.bgElevated),
        )
        Text(
            text = DisplayText.title(album),
            style = PipeBombTheme.typography.trackTitle,
            color = PipeBombTheme.colors.fgPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = DisplayText.artists(album),
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ArtistCard(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
    ) {
        ArtistImage(
            resource = DisplayText.artwork(artist),
            modifier = Modifier
                .size(CardWidth)
                .clip(CircleShape)
                .background(PipeBombTheme.colors.bgElevated),
            contentScale = ContentScale.Crop,
        )
        Text(
            text = DisplayText.title(artist),
            style = PipeBombTheme.typography.trackTitle,
            color = PipeBombTheme.colors.fgPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    ownerOverride: String? = null,
) {
    Column(
        modifier = modifier
            .width(CardWidth)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(playlist),
            modifier = Modifier
                .size(CardWidth)
                .clip(PipeBombTheme.shapes.medium)
                .background(PipeBombTheme.colors.bgElevated),
        )
        Text(
            text = DisplayText.title(playlist),
            style = PipeBombTheme.typography.trackTitle,
            color = PipeBombTheme.colors.fgPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        (ownerOverride ?: DisplayText.ownerName(playlist))?.let { owner ->
            Text(
                text = owner,
                style = PipeBombTheme.typography.bodySecondary,
                color = PipeBombTheme.colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun AlbumRow(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .clickable(onClick = onClick)
            .padding(PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(album),
            modifier = Modifier
                .size(56.dp)
                .clip(PipeBombTheme.shapes.small),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = DisplayText.title(album),
                style = PipeBombTheme.typography.trackTitle,
                color = PipeBombTheme.colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = DisplayText.artists(album),
                style = PipeBombTheme.typography.metadata,
                color = PipeBombTheme.colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ArtistRow(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .clickable(onClick = onClick)
            .padding(PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        ArtistImage(
            resource = DisplayText.artwork(artist),
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape),
        )
        Text(
            text = DisplayText.title(artist),
            style = PipeBombTheme.typography.trackTitle,
            color = PipeBombTheme.colors.fgPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .clickable(onClick = onClick)
            .padding(PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        ResourceImage(
            resource = DisplayText.artwork(playlist),
            modifier = Modifier
                .size(56.dp)
                .clip(PipeBombTheme.shapes.small),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = DisplayText.title(playlist),
                style = PipeBombTheme.typography.trackTitle,
                color = PipeBombTheme.colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DisplayText.ownerName(playlist)?.let { owner ->
                Text(
                    text = owner,
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.fgSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
