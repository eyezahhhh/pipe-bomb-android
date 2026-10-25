package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Resource
import net.pipebomb.android.data.download.DownloadItem
import net.pipebomb.android.data.download.DownloadState

@Composable
fun DownloadRow(
    item: DownloadItem,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    val entity = item.entity
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PipeBombTheme.shapes.small)
            .clickable(enabled = item.state == DownloadState.Completed, onClick = onPlay)
            .padding(vertical = PipeBombTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        ResourceImage(
            resource = entity.artworkUrl?.let { Resource(url = it) },
            modifier = Modifier
                .size(48.dp)
                .clip(PipeBombTheme.shapes.small)
                .background(colors.bgElevated),
            contentDescription = null,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = entity.title,
                style = PipeBombTheme.typography.trackTitle,
                color = colors.fgPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entity.artist ?: "Unknown Artist",
                style = PipeBombTheme.typography.metadata,
                color = colors.fgSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText(item),
                style = PipeBombTheme.typography.metadata,
                color = when (item.state) {
                    DownloadState.Failed -> colors.error
                    DownloadState.Completed -> colors.accentPrimary
                    DownloadState.Paused -> colors.warning
                    else -> colors.fgTertiary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.state == DownloadState.Downloading && entity.bytesTotal > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                        .height(2.dp)
                        .background(colors.borderSubtle),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progress)
                            .height(2.dp)
                            .background(colors.accentPrimary),
                    )
                }
            }
        }
        when (item.state) {
            DownloadState.Failed -> PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_refresh),
                contentDescription = "Retry download",
                onClick = onRetry,
                tone = PipeBombIconButtonTone.Neutral,
            )

            else -> PipeBombIconButton(
                icon = ImageVector.vectorResource(R.drawable.ic_tabler_trash),
                contentDescription = "Remove download",
                onClick = onRemove,
                tone = PipeBombIconButtonTone.Neutral,
            )
        }
    }
}

private fun statusText(item: DownloadItem): String = when (item.state) {
    DownloadState.Queued -> "Queued"
    DownloadState.Downloading -> {
        val percent = (item.progress * 100f).toInt()
        if (item.entity.bytesTotal > 0) {
            "$percent% · ${formatBytes(item.entity.bytesDownloaded)} of ${formatBytes(item.entity.bytesTotal)}"
        } else {
            "${formatBytes(item.entity.bytesDownloaded)} downloaded"
        }
    }

    DownloadState.Paused -> item.entity.lastError ?: "Paused"
    DownloadState.Completed -> "Downloaded"
    DownloadState.Failed -> item.entity.lastError ?: "Download failed"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(java.util.Locale.getDefault(), "%.1f MB", mb)
    return String.format(java.util.Locale.getDefault(), "%.2f GB", mb / 1024.0)
}
