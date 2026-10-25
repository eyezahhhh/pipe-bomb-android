package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun OfflineBanner(
    onOpenDownloads: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.warningBg)
            .clickable(onClick = onOpenDownloads)
            .padding(horizontal = PipeBombTheme.spacing.md, vertical = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm),
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_cloud_off),
            contentDescription = null,
            tint = colors.warning,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Offline · Showing downloads",
            style = PipeBombTheme.typography.metadata,
            color = colors.fgPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Downloads",
            style = PipeBombTheme.typography.metadata,
            color = colors.accentPrimary,
        )
    }
}
