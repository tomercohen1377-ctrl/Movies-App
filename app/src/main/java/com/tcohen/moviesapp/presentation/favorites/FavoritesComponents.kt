package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

/**
 * Paginated 2-column favorites grid backed by [MovieGrid].
 *
 * Each card is wrapped in a [SwipeToDismissBox] that triggers [FavoritesIntent.RemoveFavorite]
 * when the user swipes end-to-start. Swipe is disabled when [isOffline] is `true`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesGrid(
    favorites: LazyPagingItems<Movie>,
    isOffline: Boolean,
    onIntent: (FavoritesIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    MovieGrid(movies = favorites, modifier = modifier) { movie ->
        if (movie == null) return@MovieGrid

        val dismissState = rememberSwipeToDismissBoxState()

        // Trigger removal only when online — swipe is a write operation.
        LaunchedEffect(dismissState.currentValue) {
            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart && !isOffline) {
                onIntent(FavoritesIntent.RemoveFavorite(movie))
            }
        }

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            // Swipe is fully disabled when offline so the card stays put.
            enableDismissFromEndToStart = !isOffline,
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(10.dp))
                        .padding(end = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Remove from favorites",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        ) {
            MovieCard(
                movie = movie,
                onClick = { onIntent(FavoritesIntent.OpenDetail(movie.id)) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

/** Full-screen placeholder shown when the favorites list is empty. */
@Composable
internal fun EmptyFavoritesState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No saved movies yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Tap the heart on any movie to save it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyFavoritesStatePreview() {
    MoviesAppTheme {
        EmptyFavoritesState()
    }
}
