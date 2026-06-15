package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.presentation.common.MoviePosterImage
import com.tcohen.moviesapp.presentation.common.RatingBadge
import com.tcohen.moviesapp.util.TmdbImageUrl

/**
 * Stateless composable that renders all textual and visual metadata for a movie:
 * poster thumbnail, title, release year, runtime, rating badge, genre chips,
 * optional tagline, and overview.
 *
 * Intended to be embedded inside a scrollable parent such as [MovieDetailContent].
 */
@Composable
fun MovieMetadata(
    movie: MovieDetail,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {

        // Poster thumbnail + title block
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Card(shape = RoundedCornerShape(8.dp)) {
                MoviePosterImage(
                    imageUrl = TmdbImageUrl.posterLarge(movie.posterPath),
                    contentDescription = movie.title,
                    modifier = Modifier
                        .width(100.dp)
                        .aspectRatio(2f / 3f)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = movie.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (movie.releaseDate.length >= RELEASE_YEAR_CHAR_COUNT) {
                    Text(
                        text = movie.releaseDate.take(RELEASE_YEAR_CHAR_COUNT),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                movie.runtime?.let { runtime ->
                    Text(
                        text = "${runtime}m",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                RatingBadge(rating = movie.voteAverage)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Genre chips — horizontal scroll row (works for 2–5 chips, no experimental API needed)
        if (movie.genres.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                movie.genres.forEach { genre ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(genre.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Tagline
        if (!movie.tagline.isNullOrBlank()) {
            Text(
                text = "\"${movie.tagline}\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Overview
        Text(
            text = movie.overview,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** Number of leading characters in `Movie.releaseDate` that form the 4-digit year. */
private const val RELEASE_YEAR_CHAR_COUNT = 4
