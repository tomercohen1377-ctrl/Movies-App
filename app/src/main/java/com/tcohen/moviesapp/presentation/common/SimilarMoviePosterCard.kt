package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

private object SimilarCardDefaults {
    /** Aspect ratio matches the standard movie poster (2 wide × 3 tall). */
    const val ASPECT_RATIO = 2f / 3f

    /** Fixed poster width — the parent LazyRow gives it a sensible size on every device. */
    val POSTER_WIDTH = 110.dp

    /** Same elevation as the regular `MovieCard` for visual consistency. */
    val ELEVATION = 3.dp

    /** Same corner radius as the regular `MovieCard`. */
    val CORNER_RADIUS = 8.dp
}

/**
 * Compact poster card used inside the "More like this" horizontal carousel.
 *
 * Layout:
 *  - Standard 2:3 poster image (top)
 *  - Title and year underneath (bottom)
 *
 * Distinguished from the full [MovieCard] by:
 *  - Smaller fixed width (carousel-friendly)
 *  - Title and metadata shown *below* the card, not as an overlay scrim
 *    (poster-only corner-of-screen cards read better when text is below).
 *
 * Carries `testTag("similar_movie_card")` so the UI/journey tests can identify it.
 */
@Composable
fun SimilarMoviePosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(SimilarCardDefaults.POSTER_WIDTH)
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(SimilarCardDefaults.CORNER_RADIUS),
            elevation = CardDefaults.cardElevation(defaultElevation = SimilarCardDefaults.ELEVATION),
            modifier = Modifier
                .aspectRatio(SimilarCardDefaults.ASPECT_RATIO)
                .clip(RoundedCornerShape(SimilarCardDefaults.CORNER_RADIUS))
                .semantics { testTag = "similar_movie_card" }
        ) {
            MoviePosterImage(
                imageUrl = com.tcohen.moviesapp.util.TmdbImageUrl.poster(movie.posterPath),
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = movie.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        Text(
            // Show only the year — releaseDate is "YYYY-MM-DD".
            text = movie.releaseDate.take(RELEASE_YEAR_CHAR_COUNT),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val RELEASE_YEAR_CHAR_COUNT = 4

@Preview(showBackground = true)
@Composable
private fun SimilarMoviePosterCardPreview() {
    MoviesAppTheme {
        SimilarMoviePosterCard(
            movie = Movie(
                id = 1,
                title = "Dune: Part Two",
                overview = "Paul Atreides unites with Chani and the Fremen.",
                posterPath = null,
                backdropPath = null,
                releaseDate = "2024-03-01",
                voteAverage = 8.5,
                voteCount = 12340
            ),
            onClick = {}
        )
    }
}
