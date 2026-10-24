package net.pipebomb.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import net.pipebomb.android.R
import net.pipebomb.android.core.design.theme.PipeBombTheme
import net.pipebomb.android.core.model.Resource

@Composable
fun ResourceImage(
    resource: Resource?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = resource?.url?.takeIf { it.isNotBlank() }
    if (url == null) {
        AlbumArtFallback(modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(Modifier.fillMaxSize()) { AlbumArtFallback(Modifier.fillMaxSize()) }
        },
        error = {
            Box(Modifier.fillMaxSize()) { AlbumArtFallback(Modifier.fillMaxSize()) }
        },
    )
}

@Composable
fun ArtistImage(
    resource: Resource?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = resource?.url?.takeIf { it.isNotBlank() }
    if (url == null) {
        ArtistArtFallback(modifier)
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        loading = {
            Box(Modifier.fillMaxSize()) { ArtistArtFallback(Modifier.fillMaxSize()) }
        },
        error = {
            Box(Modifier.fillMaxSize()) { ArtistArtFallback(Modifier.fillMaxSize()) }
        },
    )
}

@Composable
fun ArtistArtFallback(modifier: Modifier = Modifier) {
    val colors = PipeBombTheme.colors
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF2E2E2E), Color(0xFF181E1B)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_tabler_user),
            contentDescription = null,
            tint = colors.accentPrimary.copy(alpha = 0.35f),
            modifier = Modifier.size(32.dp),
        )
    }
}
