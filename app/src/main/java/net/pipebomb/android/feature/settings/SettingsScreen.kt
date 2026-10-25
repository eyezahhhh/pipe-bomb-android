package net.pipebomb.android.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.data.storage.StorageLocation
import net.pipebomb.android.ui.components.PipeBombTextField

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStorageChooser by remember { mutableStateOf(false) }
    var pendingStorage by remember { mutableStateOf<StorageLocation?>(null) }
    var showReserveChooser by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PipeBombTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xl),
    ) {
        Section("Account") {
            InfoRow("Signed in as", state.username.ifBlank { "Not signed in" })
            ActionRow("Sign out", onClick = viewModel::logout)
        }
        Section("Server") {
            InfoRow("Server URL", state.serverUrl)
            ActionRow("Change server", onClick = viewModel::changeServer)
        }
        Section("Storage") {
            InfoRow("Location", state.storageLabel)
            if (!state.storageAvailable) {
                Text(
                    text = if (state.restartRequired) {
                        "Selected storage is available. Restart Pipe Bomb to use it."
                    } else {
                        "Selected storage is unavailable. Reconnect it or choose another location."
                    },
                    style = PipeBombTheme.typography.metadata,
                    color = if (state.restartRequired) {
                        PipeBombTheme.colors.warning
                    } else {
                        PipeBombTheme.colors.error
                    },
                    modifier = Modifier.padding(
                        horizontal = PipeBombTheme.spacing.md,
                        vertical = PipeBombTheme.spacing.sm,
                    ),
                )
            }
            if (state.restartRequired) {
                ActionRow("Restart app", onClick = viewModel::restartApp)
            }
            ActionRow("Change location", onClick = { showStorageChooser = true })
            InfoRow("Free space", formatBytes(state.freeBytes))
            InfoRow("Reserved free space", formatBytes(state.reserveBytes))
            ActionRow("Change reserve", onClick = { showReserveChooser = true })
            InfoRow("Download capacity", formatBytes(downloadCapacity(state)))
            if (state.reservePaused) {
                Text(
                    text = "Downloads are paused until storage is above the reserve.",
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.warning,
                    modifier = Modifier.padding(
                        horizontal = PipeBombTheme.spacing.md,
                        vertical = PipeBombTheme.spacing.sm,
                    ),
                )
                ActionRow("Resume downloads", onClick = viewModel::resumeDownloads)
            }
            InfoRow("Downloads", formatBytes(state.downloadedBytes))
            InfoRow("Cached audio", formatBytes(state.cacheBytes))
            ActionRow("Clear cached audio", onClick = viewModel::clearCache)
            ActionRow("Remove all downloads", onClick = viewModel::removeAllDownloads)
        }
        Section("About") {
            InfoRow("App version", state.appVersion)
            InfoRow("API contract", "0.2.0")
        }
    }

    if (showStorageChooser) {
        StorageChooserDialog(
            state = state,
            onDismiss = { showStorageChooser = false },
            onSelect = { location ->
                showStorageChooser = false
                if (location.id != state.selectedLocationId) pendingStorage = location
            },
        )
    }

    pendingStorage?.let { location ->
        AlertDialog(
            onDismissRequest = { pendingStorage = null },
            containerColor = PipeBombTheme.colors.bgElevated,
            title = {
                Text(
                    text = "Change download location?",
                    style = PipeBombTheme.typography.sectionHeading,
                    color = PipeBombTheme.colors.fgPrimary,
                )
            },
            text = {
                Text(
                    text = "Changing to ${location.label} deletes all downloads and cached audio. " +
                        "Pipe Bomb will restart.",
                    style = PipeBombTheme.typography.bodySecondary,
                    color = PipeBombTheme.colors.fgSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingStorage = null
                        viewModel.switchStorage(location)
                    },
                ) {
                    Text("Change and delete", color = PipeBombTheme.colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStorage = null }) {
                    Text("Cancel", color = PipeBombTheme.colors.fgSecondary)
                }
            },
        )
    }

    if (showReserveChooser) {
        ReserveChooserDialog(
            current = state.reserveBytes,
            capacity = state.totalBytes,
            onDismiss = { showReserveChooser = false },
            onSelect = { bytes ->
                showReserveChooser = false
                viewModel.setReserveBytes(bytes)
            },
        )
    }
}

@Composable
private fun StorageChooserDialog(
    state: SettingsUiState,
    onDismiss: () -> Unit,
    onSelect: (StorageLocation) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PipeBombTheme.colors.bgElevated,
        title = {
            Text(
                text = "Download location",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
            ) {
                state.storageLocations.forEach { location ->
                    val selected = location.id == state.selectedLocationId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(PipeBombTheme.shapes.medium)
                            .clickable { onSelect(location) }
                            .padding(
                                horizontal = PipeBombTheme.spacing.sm,
                                vertical = PipeBombTheme.spacing.md,
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = location.label,
                                style = PipeBombTheme.typography.body,
                                color = PipeBombTheme.colors.fgPrimary,
                            )
                            Text(
                                text = "${formatBytes(location.availableBytes)} free",
                                style = PipeBombTheme.typography.metadata,
                                color = PipeBombTheme.colors.fgTertiary,
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_check_filled),
                                contentDescription = "Selected",
                                tint = PipeBombTheme.colors.accentPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PipeBombTheme.colors.fgPrimary)
            }
        },
    )
}

@Composable
private fun ReserveChooserDialog(
    current: Long,
    capacity: Long,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
) {
    val presets = listOf(
        0L to "Off (no reserve)",
        500L * MIB to "500 MB",
        1L * GIB to "1 GB",
        2L * GIB to "2 GB",
        5L * GIB to "5 GB",
        10L * GIB to "10 GB",
    )
    var selected by remember(current) { mutableLongStateOf(current) }
    var customText by remember {
        mutableStateOf(
            if (presets.any { it.first == current }) "" else formatCustomInput(current),
        )
    }
    var customError by remember { mutableStateOf(false) }
    val customBytes = customText.trim().takeIf { it.isNotEmpty() }?.let(::parseMegabytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PipeBombTheme.colors.bgElevated,
        title = {
            Text(
                text = "Reserved free space",
                style = PipeBombTheme.typography.sectionHeading,
                color = PipeBombTheme.colors.fgPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs),
            ) {
                Text(
                    text = "Downloads pause before free space drops below this reserve.",
                    style = PipeBombTheme.typography.metadata,
                    color = PipeBombTheme.colors.fgSecondary,
                )
                presets.forEach { (bytes, label) ->
                    SelectableRow(
                        label = label,
                        selected = customText.isBlank() && selected == bytes,
                        onClick = {
                            selected = bytes
                            customText = ""
                            customError = false
                        },
                    )
                }
                PipeBombTextField(
                    value = customText,
                    onValueChange = {
                        customText = it.filter { char -> char.isDigit() || char == '.' }
                        customError = false
                    },
                    label = "Custom reserve (MB)",
                    placeholder = "e.g. 1500",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = customError,
                )
                if (selected == 0L && customText.isBlank()) {
                    Text(
                        text = "Without a reserve, downloads can fill the selected storage.",
                        style = PipeBombTheme.typography.metadata,
                        color = PipeBombTheme.colors.warning,
                    )
                }
                if (customError) {
                    Text(
                        text = "Enter a value between 0 and ${formatBytes(capacity)}.",
                        style = PipeBombTheme.typography.metadata,
                        color = PipeBombTheme.colors.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = if (customText.isNotBlank()) {
                        val parsed = customBytes
                        if (parsed == null || parsed < 0 || (capacity > 0 && parsed >= capacity)) {
                            customError = true
                            return@TextButton
                        }
                        parsed
                    } else {
                        selected
                    }
                    if (capacity > 0 && value >= capacity) {
                        customError = true
                        return@TextButton
                    }
                    onSelect(value)
                },
            ) {
                Text("Apply", color = PipeBombTheme.colors.accentPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PipeBombTheme.colors.fgSecondary)
            }
        },
    )
}

@Composable
private fun SelectableRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PipeBombTheme.shapes.medium)
            .background(
                if (selected) PipeBombTheme.colors.bgSurface else PipeBombTheme.colors.bgElevated,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = PipeBombTheme.spacing.sm, vertical = PipeBombTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        Text(
            text = label,
            style = PipeBombTheme.typography.body,
            color = PipeBombTheme.colors.fgPrimary,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_check_filled),
                contentDescription = "Selected",
                tint = PipeBombTheme.colors.accentPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
        Text(
            text = title,
            style = PipeBombTheme.typography.metadata,
            color = PipeBombTheme.colors.fgTertiary,
            modifier = Modifier.padding(horizontal = PipeBombTheme.spacing.xs),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(PipeBombTheme.shapes.medium)
                .background(PipeBombTheme.colors.bgSurface),
        ) {
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = PipeBombTheme.spacing.md, vertical = PipeBombTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgSecondary,
        )
        Text(
            text = value,
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = PipeBombTheme.spacing.md),
        )
    }
    HorizontalDivider(color = PipeBombTheme.colors.borderSubtle)
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = PipeBombTheme.spacing.md, vertical = PipeBombTheme.spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgPrimary,
        )
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_chevron_right),
            contentDescription = null,
            tint = PipeBombTheme.colors.fgTertiary,
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun downloadCapacity(state: SettingsUiState): Long =
    (state.freeBytes - state.reserveBytes).coerceAtLeast(0L)

private fun formatCustomInput(bytes: Long): String {
    val mb = bytes / MIB.toDouble()
    return if (mb == mb.toLong().toDouble()) {
        mb.toLong().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.1f", mb)
    }
}

private fun parseMegabytes(value: String): Long? =
    value.toDoubleOrNull()?.let { (it * MIB).toLong() }

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    val gb = mb / 1024.0
    if (gb < 1024) return String.format(java.util.Locale.getDefault(), "%.2f GB", gb)
    return String.format(java.util.Locale.getDefault(), "%.2f TB", gb / 1024.0)
}

private const val MIB = 1024L * 1024L
private const val GIB = 1024L * MIB
