package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

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

@Preview(showBackground = true)
@Composable
private fun MoviePosterImagePreview() {
    MoviesAppTheme {

        MoviePosterImage(
            imageUrl = null,
            contentDescription = "Movie poster",
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(2f / 3f)
        )
    }
}
