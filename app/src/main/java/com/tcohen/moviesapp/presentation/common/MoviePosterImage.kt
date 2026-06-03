package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage

/**
 * Loads a TMDB image URL via Coil with:
 * - A shimmer placeholder while loading
 * - A solid colour fallback on error
 * - Crossfade transition on success (configured globally in [ImageLoader])
 */
@Composable
fun MoviePosterImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            ShimmerEffect()
        },
        error = {
            Box(modifier = Modifier.matchParentSize()) {
                ShimmerEffect()
            }
        }
    )
}
