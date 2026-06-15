package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tcohen.moviesapp.presentation.common.TrailerPlayerSection
import com.tcohen.moviesapp.util.TmdbImageUrl

/**
 * Scrollable body of the movie detail screen — trailer/backdrop at the top followed by
 * [MovieMetadata]. Extracted so it can be rendered behind the loading overlay while the
 * YouTube player warms up, without cluttering [MovieDetailScreen].
 *
 * @param uiState The [MovieDetailUiState.Success] to render.
 * @param onPlayerReady Callback fired once the YouTube player has initialised.
 */
@Composable
fun MovieDetailContent(
    uiState: MovieDetailUiState.Success,
    onPlayerReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {

        // Trailer player OR backdrop/poster image at the very top.
        // Capture in a local val so Kotlin can smart-cast across the module boundary.
        val trailerKey = uiState.trailerKey
        if (trailerKey != null) {
            TrailerPlayerSection(
                trailerKey = trailerKey,
                onPlayerReady = onPlayerReady
            )
        } else {
            AsyncImage(
                model = TmdbImageUrl.backdrop(uiState.movie.backdropPath)
                    ?: TmdbImageUrl.posterLarge(uiState.movie.posterPath),
                contentDescription = uiState.movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
        }

        // All textual and visual metadata
        MovieMetadata(movie = uiState.movie)

        // Bottom spacer so the FAB doesn't overlap the last line of text
        Spacer(modifier = Modifier.height(88.dp))
    }
}
