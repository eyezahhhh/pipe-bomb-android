package net.pipebomb.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.AlbumEphemeralContent
import net.pipebomb.android.core.model.ArtistEphemeralContent
import net.pipebomb.android.core.model.EphemeralContentState
import net.pipebomb.android.core.model.EphemeralSource
import net.pipebomb.android.core.model.EphemeralTrack
import net.pipebomb.android.core.model.Track
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.core.model.toTrack

fun LazyListScope.artistEphemeralSection(
    state: EphemeralContentState<ArtistEphemeralContent>,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (Track) -> Unit,
    onSelectSource: (EphemeralSource) -> Unit,
) {
    if (!state.visible) return

    if (state.isLoadingSources) {
        item { EphemeralLoading() }
        return
    }

    if (state.isIdentity) {
        val content = state.content
        if (content == null) {
            if (state.isLoadingContent) item { EphemeralLoading() }
            return
        }
        item { EphemeralHeading(stringResource(R.string.ephemeral_from_other_sources)) }
        item {
            EphemeralSourceTabs(
                sources = listOf(content.source),
                selected = content.source,
                onSelect = onSelectSource,
            )
        }
        if (state.isLoadingContent) {
            item { EphemeralLoading() }
            return
        }
        artistContentItems(
            content = content,
            currentKey = currentKey,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onOpenAlbum = onOpenAlbum,
            onTrackMore = onTrackMore,
        )
        return
    }

    if (state.sources.isEmpty()) {
        if (state.isLoadingContent) item { EphemeralLoading() }
        return
    }

    item { EphemeralHeading(stringResource(R.string.ephemeral_from_other_sources)) }
    item {
        EphemeralSourceTabs(
            sources = state.sources,
            selected = state.selectedSource,
            onSelect = onSelectSource,
        )
    }

    if (state.isLoadingContent) {
        item { EphemeralLoading() }
        return
    }
    val content = state.content ?: return
    artistContentItems(
        content = content,
        currentKey = currentKey,
        isPlaying = isPlaying,
        onPlay = onPlay,
        onOpenAlbum = onOpenAlbum,
        onTrackMore = onTrackMore,
    )
}

fun LazyListScope.albumEphemeralSection(
    state: EphemeralContentState<AlbumEphemeralContent>,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (Track) -> Unit,
    onSelectSource: (EphemeralSource) -> Unit,
) {
    if (!state.visible) return

    if (state.isLoadingSources) {
        item { EphemeralLoading() }
        return
    }

    if (state.isIdentity) {
        val content = state.content
        if (content == null) {
            if (state.isLoadingContent) item { EphemeralLoading() }
            return
        }
        item {
            Text(
                text = stringResource(R.string.ephemeral_tracklist_from, content.source.name),
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
                modifier = Modifier.padding(
                    top = PipeBombTheme.spacing.xl - PipeBombTheme.spacing.listGap,
                ),
            )
        }
        ephemeralTrackItems(
            tracks = content.tracks,
            currentKey = currentKey,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onTrackMore = onTrackMore,
        )
        return
    }

    if (state.sources.isEmpty()) {
        if (state.isLoadingContent) item { EphemeralLoading() }
        return
    }

    item { EphemeralHeading(stringResource(R.string.ephemeral_from_other_sources)) }
    item {
        EphemeralSourceTabs(
            sources = state.sources,
            selected = state.selectedSource,
            onSelect = onSelectSource,
        )
    }

    if (state.isLoadingContent) {
        item { EphemeralLoading() }
        return
    }
    val content = state.content ?: return

    if (content.tracks.isNotEmpty()) {
        item { EphemeralHeading(stringResource(R.string.ephemeral_tracks)) }
        ephemeralTrackItems(
            tracks = content.tracks,
            currentKey = currentKey,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onTrackMore = onTrackMore,
        )
    }
}

private fun LazyListScope.artistContentItems(
    content: ArtistEphemeralContent,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onTrackMore: (Track) -> Unit,
) {
    if (content.albums.isNotEmpty()) {
        item { EphemeralHeading(stringResource(R.string.ephemeral_albums)) }
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
            ) {
                items(
                    items = content.albums,
                    key = { album -> album.uuid ?: album.identityKey ?: album.hashCode().toString() },
                ) { album ->
                    AlbumCard(
                        album = album,
                        onClick = {
                            val id = album.uuid ?: album.identityKey
                            if (id != null) onOpenAlbum(id)
                        },
                    )
                }
            }
        }
    }

    if (content.tracks.isNotEmpty()) {
        item { EphemeralHeading(stringResource(R.string.ephemeral_tracks)) }
        ephemeralTrackItems(
            tracks = content.tracks,
            currentKey = currentKey,
            isPlaying = isPlaying,
            onPlay = onPlay,
            onTrackMore = onTrackMore,
        )
    }
}

@Composable
private fun EphemeralSourceTabs(
    sources: List<EphemeralSource>,
    selected: EphemeralSource?,
    onSelect: (EphemeralSource) -> Unit,
) {
    val selectedIndex = sources.indexOfFirst {
        it.pluginId == selected?.pluginId && it.id == selected.id
    }.coerceAtLeast(0)
    PipeBombTabs(
        options = sources.map { it.name },
        selectedIndex = selectedIndex,
        onSelect = { index -> sources.getOrNull(index)?.let(onSelect) },
    )
}

@Composable
private fun EphemeralHeading(text: String) {
    Text(
        text = text,
        style = PipeBombTheme.typography.sectionHeading,
        color = PipeBombTheme.colors.fgPrimary,
        modifier = Modifier.padding(
            top = PipeBombTheme.spacing.xl - PipeBombTheme.spacing.listGap,
        ),
    )
}

private fun LazyListScope.ephemeralTrackItems(
    tracks: List<EphemeralTrack>,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (Track) -> Unit,
) {
    val keys = tracks.map { it.key }
    itemsIndexed(
        items = tracks,
        key = { _, track -> track.key.toString() },
    ) { index, track ->
        EphemeralTrackRow(
            track = track,
            isCurrent = track.key.toString() == currentKey,
            isPlaying = isPlaying,
            onClick = { onPlay(keys, index) },
            onPlay = { onPlay(keys, index) },
            onMore = { onTrackMore(track.toTrack()) },
            trackNumber = index + 1,
        )
    }
}

@Composable
private fun EphemeralLoading() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        PipeBombSpinner()
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun ArtistEphemeralSectionPreview() {
    val source = EphemeralSource(id = "source", pluginId = "plugin", name = "Other source")
    PipeBombTheme {
        LazyColumn {
            artistEphemeralSection(
                state = EphemeralContentState(
                    sources = listOf(source),
                    selectedSource = source,
                    content = ArtistEphemeralContent(
                        source = source,
                        tracks = listOf(
                            EphemeralTrack(
                                trackId = "track-1",
                                pluginId = "plugin",
                                libraryId = "library",
                                title = "Ephemeral track",
                            ),
                        ),
                    ),
                ),
                currentKey = null,
                isPlaying = false,
                onPlay = { _, _ -> },
                onOpenAlbum = {},
                onTrackMore = {},
                onSelectSource = {},
            )
        }
    }
}
