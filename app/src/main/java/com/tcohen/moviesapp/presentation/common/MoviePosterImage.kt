package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

/**
 * Loads a TMDB image URL via Coil.
 *
 * Uses [AsyncImage] (not [coil.compose.SubcomposeAsyncImage]) so that no extra
 * subcomposition occurs per list cell — this keeps the grid scroll smooth.
 *
 * A neutral surface-variant background visible while the image downloads acts as
 * a lightweight placeholder without triggering additional animation frames.
 * Crossfade is configured on the global [ImageLoader] so it applies here automatically.
 */
@Composable
fun MoviePosterImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    AsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)
    )
}
