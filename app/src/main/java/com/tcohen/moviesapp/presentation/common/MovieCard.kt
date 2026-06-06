package com.tcohen.moviesapp.presentation.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.tcohen.moviesapp.util.TmdbImageUrl

private object MovieCardDefaults {
    /** Aspect ratio of the poster card: 2-wide by 3-tall (standard movie poster format). */
    const val ASPECT_RATIO = 2f / 3f

    /** Corner radius for the card shape. */
    val CORNER_RADIUS = 10.dp

    /** Default elevation for the card shadow. */
    val ELEVATION = 4.dp

    /** Fraction of the card height covered by the bottom gradient scrim. */
    const val SCRIM_HEIGHT_FRACTION = 0.45f

    /** Opacity of the darkest point of the bottom gradient scrim. */
    const val SCRIM_GRADIENT_ALPHA = 0.85f
}

/**
 * Full-bleed poster card shown in the movie grid.
 *
 * Layout:
 * - Poster image fills the entire card (2:3 aspect ratio)
 * - Semi-transparent gradient scrim at the bottom
 * - Movie title + [RatingBadge] overlaid on the scrim
 */
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(MovieCardDefaults.ASPECT_RATIO)
            .semantics { testTag = "movie_card" },
        shape = RoundedCornerShape(MovieCardDefaults.CORNER_RADIUS),
        elevation = CardDefaults.cardElevation(defaultElevation = MovieCardDefaults.ELEVATION)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            // Poster image — full bleed
            MoviePosterImage(
                imageUrl = TmdbImageUrl.poster(movie.posterPath),
                contentDescription = movie.title,
                modifier = Modifier.fillMaxSize()
            )

            // Bottom gradient scrim for text readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(MovieCardDefaults.SCRIM_HEIGHT_FRACTION)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = MovieCardDefaults.SCRIM_GRADIENT_ALPHA))
                        )
                    )
            )

            // Title + rating overlaid on scrim
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                RatingBadge(rating = movie.voteAverage)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MovieCardPreview() {
    MoviesAppTheme {
        MovieCard(
            movie = Movie(
                id = 1,
                title = "Dune: Part Two",
                overview = "Follow the mythic journey of Paul Atreides.",
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
