package net.pipebomb.android.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.pipebomb.android.core.attributes.DisplayText
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.R
import net.pipebomb.android.core.model.PlaylistMember
import net.pipebomb.android.core.model.PlaylistVisibility
import net.pipebomb.android.core.model.User
import net.pipebomb.android.core.model.TrackKey
import net.pipebomb.android.ui.components.MediaActions
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombCheckbox
import net.pipebomb.android.ui.components.PipeBombIconButton
import net.pipebomb.android.ui.components.PipeBombIconButtonSize
import net.pipebomb.android.ui.components.PipeBombIconButtonTone
import net.pipebomb.android.ui.components.PipeBombTextField
import net.pipebomb.android.ui.components.PipeBombButtonVariant
import net.pipebomb.android.ui.components.PipeBombSpinner
import net.pipebomb.android.ui.components.ResourceImage
import net.pipebomb.android.ui.components.TrackRow
import net.pipebomb.android.ui.components.TrackChunkErrorRow
import net.pipebomb.android.ui.components.TrackSkeletonRow

@Composable
fun PlaylistScreen(
    playlistUuid: String,
    currentKey: String?,
    isPlaying: Boolean,
    onPlay: (List<TrackKey>, Int) -> Unit,
    onTrackMore: (net.pipebomb.android.core.model.Track) -> Unit = {},
    onPlayNext: (List<TrackKey>) -> Unit = {},
    onAddToQueue: (List<TrackKey>) -> Unit = {},
    onDeleted: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(playlistUuid) { viewModel.load(playlistUuid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playlist = state.playlist
    val scope = rememberCoroutineScope()
    var optionsOpen by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var memberQuery by remember { mutableStateOf("") }
    var loadingAll by remember { mutableStateOf(false) }

    fun loadAllThen(action: (List<net.pipebomb.android.core.model.Track>) -> Unit) {
        if (loadingAll) return
        scope.launch {
            loadingAll = true
            try {
                val all = viewModel.loadAllTracks()
                if (all.isNotEmpty()) action(all)
            } finally {
                loadingAll = false
            }
        }
    }

    LaunchedEffect(state.deleted) {
        if (state.deleted) onDeleted()
    }
    LaunchedEffect(showMembers, memberQuery) {
        if (showMembers) {
            delay(350)
            viewModel.searchUsers(memberQuery)
        }
    }

    when {
        state.isLoading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            PipeBombSpinner()
        }

        state.error != null -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(state.error!!, color = PipeBombTheme.colors.error)
        }

        playlist != null -> {
            LazyColumn(
                modifier = modifier.fillMaxSize().padding(horizontal = PipeBombTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.listGap),
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(top = PipeBombTheme.spacing.sm),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
                    ) {
                        ResourceImage(
                            resource = DisplayText.artwork(playlist),
                            modifier = Modifier
                                .size(128.dp)
                                .clip(PipeBombTheme.shapes.large)
                                .background(PipeBombTheme.colors.bgElevated),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
                            Text(
                                text = DisplayText.title(playlist),
                                style = PipeBombTheme.typography.sectionHeading,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            DisplayText.ownerName(playlist)?.let { owner ->
                                Text(
                                    text = owner,
                                    style = PipeBombTheme.typography.bodySecondary,
                                    color = PipeBombTheme.colors.fgSecondary,
                                )
                            }
                            Text(
                                text = buildString {
                                    append(
                                        when (playlist.visibility) {
                                            PlaylistVisibility.Public -> "Public"
                                            PlaylistVisibility.Unlisted -> "Unlisted"
                                            PlaylistVisibility.Private -> "Private"
                                        },
                                    )
                                    append(" · ")
                                    append(
                                        if (state.totalCount == 1) {
                                            "1 track"
                                        } else {
                                            "${state.totalCount} tracks"
                                        },
                                    )
                                },
                                style = PipeBombTheme.typography.metadata,
                                color = PipeBombTheme.colors.fgTertiary,
                            )
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
                    ) {
                        MediaActions(
                            onPlay = {
                                loadAllThen { all -> onPlay(all.map { it.key }, 0) }
                            },
                            onShuffle = {
                                loadAllThen { all -> onPlay(all.shuffled().map { it.key }, 0) }
                            },
                            onPlayNext = {
                                loadAllThen { all -> onPlayNext(all.map { it.key }) }
                            },
                            onAddToEnd = {
                                loadAllThen { all -> onAddToQueue(all.map { it.key }) }
                            },
                            enabled = state.totalCount > 0,
                            playLoading = loadingAll,
                        )
                        Spacer(Modifier.weight(1f))
                        PlaylistOptionsMenu(
                            expanded = optionsOpen,
                            onExpandedChange = { optionsOpen = it },
                            canDelete = state.isOwner,
                            onContributors = {
                                optionsOpen = false
                                showMembers = true
                                viewModel.loadMembers()
                            },
                            onDelete = {
                                optionsOpen = false
                                showDelete = true
                            },
                        )
                    }
                }
                items(
                    count = state.totalCount,
                    key = { index -> index },
                ) { index ->
                    val track = state.tracksByIndex[index]
                    if (track != null) {
                        TrackRow(
                            track = track,
                            isCurrent = track.key.toString() == currentKey,
                            isPlaying = isPlaying,
                            onClick = {
                                scope.launch {
                                    val all = viewModel.loadAllTracks()
                                    if (all.isNotEmpty()) {
                                        onPlay(all.map { it.key }, index.coerceIn(0, all.lastIndex))
                                    }
                                }
                            },
                            onPlay = {},
                            onMore = { onTrackMore(track) },
                        )
                    } else {
                        val chunkIndex = index / PLAYLIST_CHUNK_SIZE
                        LaunchedEffect(chunkIndex) { viewModel.ensureChunk(chunkIndex) }
                        val chunkError = state.chunkErrors[chunkIndex]
                        if (chunkError != null && index % PLAYLIST_CHUNK_SIZE == 0) {
                            TrackChunkErrorRow(onRetry = { viewModel.ensureChunk(chunkIndex) })
                        } else {
                            TrackSkeletonRow()
                        }
                    }
                }
                item { Box(Modifier.padding(bottom = PipeBombTheme.spacing.sm)) }
            }

            if (showMembers) {
                PlaylistMembersSheet(
                    playlist = playlist,
                    state = state,
                    query = memberQuery,
                    onQueryChange = { memberQuery = it },
                    onDismiss = { showMembers = false },
                    onAdd = viewModel::addMember,
                    onToggleRole = viewModel::updateMemberRole,
                    onRemove = viewModel::removeMember,
                )
            }

            if (showDelete) {
                DeletePlaylistDialog(
                    playlistTitle = DisplayText.title(playlist),
                    isDeleting = state.isDeleting,
                    error = state.deleteError,
                    onDismiss = { if (!state.isDeleting) showDelete = false },
                    onConfirm = viewModel::deletePlaylist,
                )
            }
        }
    }
}

@Composable
private fun PlaylistOptionsMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    canDelete: Boolean,
    onContributors: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    Box {
        PipeBombIconButton(
            icon = ImageVector.vectorResource(R.drawable.ic_tabler_dots),
            contentDescription = "Playlist options",
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
                        text = "Contributors",
                        style = PipeBombTheme.typography.bodySecondary,
                        color = colors.fgPrimary,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_user),
                        contentDescription = null,
                        tint = colors.fgSecondary,
                    )
                },
                onClick = onContributors,
            )
            if (canDelete) {
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete playlist",
                            style = PipeBombTheme.typography.bodySecondary,
                            color = colors.error,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_trash),
                            contentDescription = null,
                            tint = colors.error,
                        )
                    },
                    onClick = onDelete,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistMembersSheet(
    playlist: net.pipebomb.android.core.model.Playlist,
    state: PlaylistUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (User) -> Unit,
    onToggleRole: (PlaylistMember, Boolean) -> Unit,
    onRemove: (PlaylistMember) -> Unit,
) {
    val colors = PipeBombTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val ownerUuid = playlist.owner.userUuid
    val ownerName = playlist.owner.user?.username ?: playlist.owner.botName ?: "Owner"
    val filteredResults = state.userSearchResults.filter { user ->
        user.uuid != ownerUuid && state.members.none { it.userUuid == user.uuid }
    }

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
                text = "Playlist contributors",
                style = PipeBombTheme.typography.sectionHeading,
                color = colors.fgPrimary,
            )
            Text(
                text = "Editors can add and remove tracks. Viewers can listen only.",
                style = PipeBombTheme.typography.metadata,
                color = colors.fgTertiary,
            )

            ContributorRow(
                name = ownerName,
                role = "Owner",
                showControls = false,
            )

            when {
                state.membersLoading -> Box(
                    Modifier.fillMaxWidth().padding(PipeBombTheme.spacing.lg),
                    contentAlignment = Alignment.Center,
                ) { PipeBombSpinner(size = 24.dp, strokeWidth = 3.dp) }

                state.members.isEmpty() -> Text(
                    text = "No contributors yet",
                    style = PipeBombTheme.typography.bodySecondary,
                    color = colors.fgTertiary,
                )

                else -> state.members.forEach { member ->
                    ContributorRow(
                        name = member.user?.username ?: member.userUuid,
                        role = if (member.role == "collaborator") "Editor" else "Viewer",
                        showControls = state.isOwner,
                        canEdit = member.role == "collaborator",
                        busy = state.memberActionUserUuid == member.userUuid,
                        onToggleRole = { onToggleRole(member, it) },
                        onRemove = { onRemove(member) },
                    )
                }
            }

            state.membersError?.let { error ->
                Text(
                    text = error,
                    style = PipeBombTheme.typography.metadata,
                    color = colors.error,
                )
            }

            if (state.isOwner) {
                Text(
                    text = "Add contributor",
                    style = PipeBombTheme.typography.trackTitle,
                    color = colors.fgPrimary,
                )
                PipeBombTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = "Search users",
                )
                when {
                    state.userSearchLoading -> Box(
                        Modifier.fillMaxWidth().padding(PipeBombTheme.spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) { PipeBombSpinner(size = 20.dp, strokeWidth = 2.dp) }

                    query.isNotBlank() && filteredResults.isEmpty() -> Text(
                        text = "No users found",
                        style = PipeBombTheme.typography.metadata,
                        color = colors.fgTertiary,
                    )

                    else -> filteredResults.take(5).forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(PipeBombTheme.shapes.medium)
                                .background(colors.bgSurface)
                                .clickable { onAdd(user) }
                                .padding(horizontal = PipeBombTheme.spacing.md, vertical = PipeBombTheme.spacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = user.username,
                                style = PipeBombTheme.typography.bodySecondary,
                                color = colors.fgPrimary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "Add",
                                style = PipeBombTheme.typography.bodySecondary,
                                color = colors.accentPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContributorRow(
    name: String,
    role: String,
    showControls: Boolean,
    canEdit: Boolean = false,
    busy: Boolean = false,
    onToggleRole: (Boolean) -> Unit = {},
    onRemove: () -> Unit = {},
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(colors.bgSurface)
            .padding(horizontal = PipeBombTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_user),
            contentDescription = null,
            tint = colors.fgTertiary,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = PipeBombTheme.typography.bodySecondary,
                color = colors.fgPrimary,
                maxLines = 1,
            )
            Text(
                text = role,
                style = PipeBombTheme.typography.metadata,
                color = colors.fgTertiary,
            )
        }
        if (showControls) {
            if (busy) {
                PipeBombSpinner(size = 16.dp, strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Editor",
                    style = PipeBombTheme.typography.metadata,
                    color = colors.fgTertiary,
                )
                PipeBombCheckbox(
                    checked = canEdit,
                    onCheckedChange = onToggleRole,
                )
                PipeBombIconButton(
                    icon = ImageVector.vectorResource(R.drawable.ic_tabler_trash),
                    contentDescription = "Remove contributor",
                    onClick = onRemove,
                    size = PipeBombIconButtonSize.Medium,
                    tone = PipeBombIconButtonTone.Danger,
                )
            }
        }
    }
}

@Composable
private fun DeletePlaylistDialog(
    playlistTitle: String,
    isDeleting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = PipeBombTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.bgSurface,
        titleContentColor = colors.fgPrimary,
        textContentColor = colors.fgSecondary,
        shape = PipeBombTheme.shapes.large,
        title = {
            Text(
                text = "Delete playlist?",
                style = PipeBombTheme.typography.sectionHeading,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
                Text(
                    text = "“$playlistTitle” will be permanently deleted. This cannot be undone.",
                    style = PipeBombTheme.typography.bodySecondary,
                )
                error?.let {
                    Text(
                        text = it,
                        style = PipeBombTheme.typography.metadata,
                        color = colors.error,
                    )
                }
            }
        },
        confirmButton = {
            PipeBombButton(
                text = "Delete",
                onClick = onConfirm,
                variant = PipeBombButtonVariant.Danger,
                loading = isDeleting,
            )
        },
        dismissButton = {
            PipeBombButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = PipeBombButtonVariant.Ghost,
                enabled = !isDeleting,
            )
        },
    )
}
