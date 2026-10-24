package net.pipebomb.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.ui.components.PipeBombButton
import net.pipebomb.android.ui.components.PipeBombButtonVariant

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
        verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.lg),
    ) {
        Section("Account") {
            InfoRow("Signed in as", state.username.ifBlank { "Not signed in" })
            PipeBombButton(
                text = "Sign out",
                onClick = viewModel::logout,
                variant = PipeBombButtonVariant.Secondary,
            )
        }
        Section("Server") {
            InfoRow("URL", state.serverUrl)
            PipeBombButton(
                text = "Change server",
                onClick = viewModel::changeServer,
                variant = PipeBombButtonVariant.Ghost,
            )
        }
        Section("Storage") {
            InfoRow("Cached audio", formatBytes(state.cacheBytes))
            PipeBombButton(
                text = "Clear cache",
                onClick = viewModel::clearCache,
                variant = PipeBombButtonVariant.Secondary,
            )
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
            style = PipeBombTheme.typography.sectionHeading,
            color = PipeBombTheme.colors.fgPrimary,
        )
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
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
            style = PipeBombTheme.typography.metadata,
            color = PipeBombTheme.colors.fgTertiary,
            modifier = Modifier.padding(start = PipeBombTheme.spacing.md),
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
