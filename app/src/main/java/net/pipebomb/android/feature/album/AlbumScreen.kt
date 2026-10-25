package net.pipebomb.android.feature.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.R
import net.pipebomb.android.core.attributes.AttributeReader
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.MediaActions
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    albumId: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onPlayNext: (List<TrackKey>) -> Unit,
    onAddToQueue: (List<TrackKey>) -> Unit,
    onOpenArtist: (String) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    LaunchedEffect(albumId) { viewModel.load(albumId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val album = state.album
    var optionsOpen by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        album != null -> {
            val tracks = album.tracks.orEmpty()
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
            ) {
                item {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(album),
                            modifier = Modifier
                                .size(160.dp)
                                .clip(PipeBombTheme.shapes.large)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(album),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            Text(
                                text = DisplayText.artists(album),
                                style = PipeBombTheme.typography.bodySecondary,
                                color = PipeBombTheme.colors.fgSecondary,
                            )
                            val year = AttributeReader.integer(album.attributes, "year")
                            Text(
                                text = listOfNotNull(
                                    year?.toString(),
                                    if (tracks.size == 1) "1 track" else "${tracks.size} tracks",
                                ).joinToString(" · "),
                                style = PipeBombTheme.typography.metadata,
                                color = PipeBombTheme.colors.fgTertiary,
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = PipeBombTheme.spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MediaActions(
                            onPlay = { onPlay(tracks.map { it.key }, 0) },
                            onShuffle = { onPlay(tracks.shuffled().map { it.key }, 0) },
                            onPlayNext = { onPlayNext(tracks.map { it.key }) },
                            onAddToEnd = { onAddToQueue(tracks.map { it.key }) },
                            isSaved = album.bookmarked == true,
                            onToggleSave = viewModel::toggleSave,
                            enabled = tracks.isNotEmpty(),
                        )
                        Spacer(Modifier.weight(1f))
                        AlbumOptionsMenu(
                            expanded = optionsOpen,
                            onExpandedChange = { optionsOpen = it },
                            onAddToPlaylist = {
                                optionsOpen = false
                                showPlaylists = true
                                viewModel.loadPlaylists()
                            },
                        )
                    }
                }
                itemsIndexed(
                    items = tracks,
                    key = { _, track -> track.key.toString() },
                ) { index, track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.key.toString() == currentKey,
                        isPlaying = isPlaying,
                        onClick = { onPlay(tracks.map { it.key }, index) },
                        onPlay = { onPlay(tracks.map { it.key }, index) },
                        showArtwork = false,
                        trackNumber = index + 1,
                        onMore = { onTrackMore(track) },
                    )
                }
                item { Box(Modifier.padding(bottom = PipeBombTheme.spacing.sm)) }
            }

            if (showPlaylists) {
                AddToPlaylistSheet(
                    state = state,
                    onDismiss = { showPlaylists = false },
                    onSelect = viewModel::addToPlaylist,
                )
            }
        }
    }
}

@Composable
private fun AlbumOptionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddToPlaylist: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    Box {
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_dots),
            contentDescription = "Album options",
            onClick = { onExpandedChange(true) },
            tone = PipeBombIconButtonTone.Neutral,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            containerColor = colors.bgElevated,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
        ) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = "Add to playlist",
                        style = PipeBombTheme.typography.bodySecondary,
                        color = colors.fgPrimary,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_plus),
                        contentDescription = null,
                        tint = colors.fgSecondary,
                    )
                },
                onClick = onAddToPlaylist,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToPlaylistSheet(
    state: AlbumUiState,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val colors = PipeBombTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.bgElevated,
        contentColor = colors.fgPrimary,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PipeBombTheme.spacing.lg)
                .padding(bottom = PipeBombTheme.spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
        ) {
            Text(
                text = "Add to playlist",
                style = PipeBombTheme.typography.sectionHeading,
                color = colors.fgPrimary,
            )
            when {
                state.isLoadingPlaylists -> Box(
                    Modifier.fillMaxWidth().padding(PipeBombTheme.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) { PipeBombSpinner(size = 24.dp, strokeWidth = 3.dp) }

                state.playlists.isEmpty() -> Text(
                    text = "No playlists yet",
                    style = PipeBombTheme.typography.bodySecondary,
                    color = colors.fgTertiary,
                )

                else -> state.playlists.forEach { playlist ->
                    val busy = state.isAddingToPlaylist == playlist.uuid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clip(PipeBombTheme.shapes.medium)
                            .background(colors.bgSurface)
                            .clickable(enabled = state.isAddingToPlaylist == null) {
                                onSelect(playlist.uuid)
                            }
                            .padding(horizontal = PipeBombTheme.spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = DisplayText.title(playlist),
                            style = PipeBombTheme.typography.bodySecondary,
                            color = colors.fgPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        if (busy) {
                            PipeBombSpinner(size = 18.dp, strokeWidth = 2.dp)
                        }
                    }
                }
            }
            state.addToPlaylistError?.let { error ->
                Text(
                    text = error,
                    style = PipeBombTheme.typography.metadata,
                    color = colors.error,
                )
            }
            if (state.addToPlaylistSuccess) {
                Text(
                    text = "Added to playlist",
                    style = PipeBombTheme.typography.metadata,
                    color = colors.success,
                )
            }
        }
    }
}
