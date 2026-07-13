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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tcohen.moviesapp.ai.presentation.ploexplainer.PlotExplainerSection
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.presentation.common.TrailerPlayerSection
import com.tcohen.moviesapp.presentation.moviedetail.morelikethis.MoreLikeThisSection
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.tcohen.moviesapp.util.TmdbImageUrl

/**
 * Scrollable body of the movie detail screen — trailer/backdrop at the top followed by
 * [MovieMetadata], the AI plot-explainer [PlotExplainerSection], and the
 * "More like this" carousel [MoreLikeThisSection]. Extracted so it can be rendered
 * behind the loading overlay while the YouTube player warms up, without cluttering
 * [MovieDetailScreen].
 *
 * @param uiState The [MovieDetailUiState.Success] to render.
 * @param onPlayerReady Callback fired once the YouTube player has initialised.
 * @param onNavigateToSimilar Callback fired when the user taps a poster in the
 *   "More like this" carousel; parent screen forwards to its `NavController`.
 */
@Composable
fun MovieDetailContent(
    uiState: MovieDetailUiState.Success,
    onPlayerReady: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToSimilar: (Int) -> Unit = {}
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {

        // Trailer player OR backdrop/poster image at the very top
        if (uiState.trailerKey != null) {
            TrailerPlayerSection(
                trailerKey = uiState.trailerKey,
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

        // Phase 0+ — AI plot summary section. Owns its own ViewModel and
        // state machine; the surrounding `MovieDetailViewModel` is unaware
        // of it (separation of concerns).
        val releaseYear = uiState.movie.releaseDate
            .take(RELEASE_YEAR_CHAR_COUNT)
            .toIntOrNull()
        PlotExplainerSection(
            title = uiState.movie.title,
            year = releaseYear,
            runtimeMinutes = uiState.movie.runtime,
        )

        // "More like this" — same pattern: owns its own ViewModel, parent is unaware.
        MoreLikeThisSection(
            movieId = uiState.movie.id,
            onMovieClicked = onNavigateToSimilar
        )

        // Bottom spacer so the FAB doesn't overlap the last line of text
        Spacer(modifier = Modifier.height(88.dp))
    }
}

/** Number of leading characters in `Movie.releaseDate` that form the 4-digit year. */
private const val RELEASE_YEAR_CHAR_COUNT = 4

@Preview(showBackground = true)
@Composable
private fun MovieDetailContentPreview() {
    MoviesAppTheme {
        MovieDetailContent(
            uiState = MovieDetailUiState.Success(
                movie = MovieDetail(
                    id = 1,
                    title = "Dune: Part Two",
                    overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
                    posterPath = null,
                    backdropPath = null,
                    releaseDate = "2024-03-01",
                    voteAverage = 8.5,
                    voteCount = 12340,
                    runtime = 166,
                    tagline = "Long live the fighters.",
                    genres = listOf(Genre(878, "Science Fiction"), Genre(28, "Action"))
                ),
                trailerKey = null,
                isFavorite = false
            ),
            onPlayerReady = {}
        )
    }
}
