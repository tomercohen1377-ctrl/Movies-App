package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.Genre
import com.tcohen.moviesapp.domain.model.MovieDetail
import com.tcohen.moviesapp.presentation.common.MoviePosterImage
import com.tcohen.moviesapp.presentation.common.RatingBadge
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme
import com.tcohen.moviesapp.util.TmdbImageUrl

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieMetadata(
    movie: MovieDetail,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {

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

        if (movie.genres.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                movie.genres.forEach { genre ->
                    SuggestionChip(
                        onClick = {},
                        label = { Text(genre.name) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (!movie.tagline.isNullOrBlank()) {
            Text(
                text = "\"${movie.tagline}\"",
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = movie.overview,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

private const val RELEASE_YEAR_CHAR_COUNT = 4

@Preview(showBackground = true)
@Composable
private fun MovieMetadataPreview() {
    MoviesAppTheme {
        MovieMetadata(
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
            )
        )
    }
}
