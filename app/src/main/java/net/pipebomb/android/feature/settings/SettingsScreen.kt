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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
            InfoRow("Cached audio", formatBytes(state.cacheBytes))
            ActionRow("Clear cache", onClick = viewModel::clearCache)
        }
        Section("About") {
            InfoRow("App version", state.appVersion)
            InfoRow("API contract", "0.2.0")
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

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.1f MB", mb)
    return String.format("%.2f GB", mb / 1024.0)
}
