package net.pipebomb.android.ui.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme

@Composable
fun DesignCatalog(modifier: Modifier = Modifier) {
    val colors = PipeBombTheme.colors
    val type = PipeBombTheme.typography
    val spacing = PipeBombTheme.spacing

    Column(
        modifier = modifier
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.xl),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Image(
                painter = painterResource(R.drawable.pipebomb_logo),
                contentDescription = "Pipe Bomb",
                modifier = Modifier.size(48.dp),
            )
            Text("Pipe Bomb design tokens", style = type.sectionHeading, color = colors.fgPrimary)
        }

        CatalogSection("Surfaces") {
            Swatch("bg-base", colors.bgBase)
            Swatch("bg-surface", colors.bgSurface)
            Swatch("bg-elevated", colors.bgElevated)
            Swatch("accent", colors.accentPrimary)
            Swatch("error", colors.error)
        }

        CatalogSection("Typography") {
            Text("Page title", style = type.pageTitle, color = colors.fgPrimary)
            Text("Greeting", style = type.greeting, color = colors.fgPrimary)
            Text("Section heading", style = type.sectionHeading, color = colors.fgPrimary)
            Text("Track title", style = type.trackTitle, color = colors.fgPrimary)
            Text("Body copy", style = type.body, color = colors.fgPrimary)
            Text("Metadata", style = type.metadata, color = colors.fgSecondary)
            Text("Outfit heading", style = type.hero, color = colors.accentPrimary)
            Text("Inter button", style = type.button, color = colors.fgPrimary)
        }

        CatalogSection("Spacing") {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                listOf(spacing.xs, spacing.sm, spacing.md, spacing.lg, spacing.xl, spacing.xxl)
                    .forEach { size ->
                        Box(
                            Modifier
                                .size(size)
                                .background(colors.accentPrimary, RoundedCornerShape(2.dp)),
                        )
                    }
            }
        }

        CatalogSection("Text weights") {
            listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold)
                .forEach { weight ->
                    Text(
                        text = weight.toString(),
                        style = type.body.copy(fontWeight = weight),
                        color = colors.fgSecondary,
                    )
                }
        }
    }
}

@Composable
private fun CatalogSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.sm)) {
        Text(title, style = PipeBombTheme.typography.metadata, color = PipeBombTheme.colors.fgTertiary)
        content()
    }
}

@Composable
private fun Swatch(label: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PipeBombTheme.spacing.md),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(PipeBombTheme.shapes.small)
                .background(color),
        )
        Text(label, style = PipeBombTheme.typography.body, color = PipeBombTheme.colors.fgSecondary)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF09090B, widthDp = 400, heightDp = 900)
@Composable
private fun DesignCatalogPreview() {
    PipeBombTheme { DesignCatalog(Modifier.fillMaxWidth()) }
}
