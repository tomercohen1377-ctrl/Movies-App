package com.tcohen.moviesapp.presentation.moviedetail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tcohen.moviesapp.domain.model.MovieDetail

/**
 * Scrollable textual metadata section for the movie detail screen:
 * title, release year, overview, and genre chips.
 */
@Composable
fun MovieMetadata(
    movie: MovieDetail,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title
        Text(
            text = movie.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Release year
        if (movie.releaseDate.isNotBlank()) {
            Text(
                text = movie.releaseDate.take(4),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Overview
        Text(
            text = movie.overview,
            style = MaterialTheme.typography.bodyMedium
        )

        // Genre chips — horizontal scroll row (works for 2–5 chips, no experimental API needed)
        if (movie.genres.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                movie.genres.forEach { genre ->
                    AssistChip(
                        onClick = {},
                        label = { Text(genre.name) }
                    )
                }
            }
        }
    }
}
