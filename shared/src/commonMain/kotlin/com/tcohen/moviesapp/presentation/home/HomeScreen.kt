package com.tcohen.moviesapp.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.cash.paging.LoadStateError
import app.cash.paging.LoadStateLoading
import app.cash.paging.compose.LazyPagingItems
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.common.CategoryFilterRow
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.common.OfflineBanner

/**
 * Home screen — pure composable with no DI dependency.
 * ViewModel wiring, lifecycle collection, and effect handling are done by the caller (AppNavGraph).
 *
 * @param state Current [HomeState].
 * @param movies Paged movie list. Drives the grid.
 * @param onIntent Dispatches [HomeIntent]s to the state holder.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    movies: LazyPagingItems<Movie>,
    onIntent: (HomeIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {

        // Offline banner — shown only when device has no connectivity
        OfflineBanner(isOffline = state.isOffline)

        // Category filter chip row
        CategoryFilterRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = { onIntent(HomeIntent.SelectCategory(it)) },
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Movie grid — driven by Paging 3
        when (val refreshState = movies.loadState.refresh) {
            is LoadStateLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadStateError -> {
                ErrorView(
                    message = refreshState.error.message ?: "Failed to load movies",
                    onRetry = { movies.retry() }
                )
            }
            else -> {
                MovieGrid(movies = movies) { movie ->
                    if (movie != null) {
                        MovieCard(
                            movie = movie,
                            onClick = { onIntent(HomeIntent.OpenDetail(movie.id)) }
                        )
                    } else {
                        // Lightweight colour placeholder while the page loads.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
            }
        }
    }
}
