package net.pipebomb.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.SubcomposeAsyncImage
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
