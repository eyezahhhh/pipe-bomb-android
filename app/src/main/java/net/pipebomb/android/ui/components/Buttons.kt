package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import net.pipebomb.android.core.design.theme.PipeBombTheme

enum class PipeBombButtonVariant { Primary, Secondary, Ghost, Danger }

@Composable
fun PipeBombButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: PipeBombButtonVariant = PipeBombButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = PipeBombTheme.colors
    val shape = PipeBombTheme.shapes.medium
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = PipeBombTheme.typography.button,
                color = if (enabled) textColor(variant) else colors.fgMuted,
            )
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = colors.accentPrimary,
                    strokeWidth = 2.dp,
                )
            }
        }
    }

    when (variant) {
        PipeBombButtonVariant.Primary -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.buttonPrimary,
                contentColor = colors.buttonPrimaryFg,
                disabledContainerColor = colors.buttonPrimaryDisabled,
                disabledContentColor = colors.fgMuted,
            ),
            contentPadding = ButtonDefaults.ContentPadding,
            content = { content() },
        )

        PipeBombButtonVariant.Secondary -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.buttonSecondary,
                contentColor = colors.buttonSecondaryFg,
                disabledContainerColor = colors.buttonSecondaryDisabled,
                disabledContentColor = colors.fgMuted,
            ),
            contentPadding = ButtonDefaults.ContentPadding,
            content = { content() },
        )

        PipeBombButtonVariant.Ghost -> TextButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = colors.fgPrimary,
                disabledContentColor = colors.fgMuted,
            ),
            content = { content() },
        )

        PipeBombButtonVariant.Danger -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled && !loading,
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.buttonDanger,
                contentColor = Color.White,
                disabledContainerColor = colors.buttonDanger.copy(alpha = 0.5f),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
            contentPadding = ButtonDefaults.ContentPadding,
            content = { content() },
        )
    }
}

@Composable
private fun textColor(variant: PipeBombButtonVariant): Color = when (variant) {
    PipeBombButtonVariant.Primary -> PipeBombTheme.colors.buttonPrimaryFg
    PipeBombButtonVariant.Secondary -> PipeBombTheme.colors.buttonSecondaryFg
    PipeBombButtonVariant.Ghost -> PipeBombTheme.colors.fgPrimary
    PipeBombButtonVariant.Danger -> Color.White
}

enum class PipeBombIconButtonSize(val container: Dp, val icon: Dp) {
    Small(48.dp, 18.dp),
    Medium(48.dp, 24.dp),
    Large(48.dp, 32.dp),
    ExtraLarge(56.dp, 42.dp),
}

enum class PipeBombIconButtonStyle { Simple, Background, Ghost }

enum class PipeBombIconButtonTone { Primary, Accent, Neutral, Danger }

@Composable
fun PipeBombIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: PipeBombIconButtonSize = PipeBombIconButtonSize.Medium,
    style: PipeBombIconButtonStyle = PipeBombIconButtonStyle.Simple,
    tone: PipeBombIconButtonTone = PipeBombIconButtonTone.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val colors = PipeBombTheme.colors
    val background = when (style) {
        PipeBombIconButtonStyle.Simple -> Color.Transparent
        PipeBombIconButtonStyle.Ghost -> Color.Transparent
        PipeBombIconButtonStyle.Background -> when (tone) {
            PipeBombIconButtonTone.Primary -> colors.buttonPrimary
            PipeBombIconButtonTone.Neutral -> colors.buttonSecondary
            PipeBombIconButtonTone.Accent -> colors.buttonPrimary
            PipeBombIconButtonTone.Danger -> colors.buttonDanger
        }
    }
    val tint = when (tone) {
        PipeBombIconButtonTone.Primary ->
            if (style == PipeBombIconButtonStyle.Background) colors.buttonPrimaryFg else colors.fgPrimary
        PipeBombIconButtonTone.Accent ->
            if (style == PipeBombIconButtonStyle.Background) colors.buttonPrimaryFg else colors.accentPrimary
        PipeBombIconButtonTone.Neutral ->
            if (style == PipeBombIconButtonStyle.Background) colors.buttonSecondaryFg else colors.fgSecondary
        PipeBombIconButtonTone.Danger -> colors.error
    }

    Box(
        modifier = modifier
            .size(size.container)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(size.icon),
                color = tint,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(size.icon),
                tint = if (enabled) tint else colors.fgMuted,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B)
@Composable
private fun ButtonsPreview() {
    PipeBombTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PipeBombButton("Primary", onClick = {})
            PipeBombButton("Secondary", onClick = {}, variant = PipeBombButtonVariant.Secondary)
            PipeBombButton("Ghost", onClick = {}, variant = PipeBombButtonVariant.Ghost)
            PipeBombButton("Danger", onClick = {}, variant = PipeBombButtonVariant.Danger)
        }
    }
}

@Composable
fun PipeBombChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PipeBombTheme.colors
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(PipeBombTheme.shapes.pill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(PipeBombTheme.shapes.pill)
                .background(if (selected) colors.buttonPrimary else colors.buttonSecondary)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = PipeBombTheme.typography.metadata,
                color = if (selected) colors.buttonPrimaryFg else colors.buttonSecondaryFg,
                maxLines = 1,
            )
        }
    }
}
