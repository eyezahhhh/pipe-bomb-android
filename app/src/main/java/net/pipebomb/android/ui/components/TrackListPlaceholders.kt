package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun TrackSkeletonRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .padding(horizontal = PipeBombTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(PipeBombTheme.shapes.small)
                .background(PipeBombTheme.colors.bgElevated),
        )
        Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.xs)) {
            Box(
                Modifier
                    .width(180.dp)
                    .height(12.dp)
                    .clip(PipeBombTheme.shapes.small)
                    .background(PipeBombTheme.colors.bgElevated),
            )
            Box(
                Modifier
                    .width(110.dp)
                    .height(10.dp)
                    .clip(PipeBombTheme.shapes.small)
                    .background(PipeBombTheme.colors.bgElevated),
            )
        }
    }
}

@Composable
fun TrackChunkErrorRow(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(PipeBombTheme.shapes.medium)
            .background(PipeBombTheme.colors.bgSurface)
            .clickable(onClick = onRetry)
            .padding(horizontal = PipeBombTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Text(
            text = "Couldn't load tracks",
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.fgSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = "Retry",
            style = PipeBombTheme.typography.bodySecondary,
            color = PipeBombTheme.colors.accentPrimary,
        )
    }
}
