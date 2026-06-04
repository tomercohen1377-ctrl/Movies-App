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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.tcohen.moviesapp.domain.model.Category
import com.tcohen.moviesapp.presentation.common.CategoryFilterRow
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.MovieCard
import com.tcohen.moviesapp.presentation.common.MovieGrid
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

@Composable
fun HomeScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movies = viewModel.moviesFlow.collectAsLazyPagingItems()

    // One-shot effects
    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToDetail -> onNavigateToDetail(effect.movieId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Offline banner — shown only when device has no connectivity
        OfflineBanner(isOffline = state.isOffline)

        // Category filter chip row
        CategoryFilterRow(
            selectedCategory = state.selectedCategory,
            onCategorySelected = { viewModel.processIntent(HomeIntent.SelectCategory(it)) },
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Movie grid — driven by Paging 3
        when (val refreshState = movies.loadState.refresh) {
            is LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is LoadState.Error -> {
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
                            onClick = { viewModel.processIntent(HomeIntent.OpenDetail(movie.id)) }
                        )
                    } else {
                        // Lightweight colour placeholder while the page loads.
                        // Avoids running continuous shimmer animations on 20+ cells simultaneously.
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

// HomeScreen itself requires Hilt injection. This preview shows the shell UI
// (offline banner + filter chips + loading spinner) with hardcoded state.
@Preview(showBackground = true)
@Composable
private fun HomeScreenLoadingPreview() {
    MoviesAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoryFilterRow(
                selectedCategory = Category.NOW_PLAYING,
                onCategorySelected = {}
            )
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenOfflineBannerPreview() {
    MoviesAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(isOffline = true)
            CategoryFilterRow(
                selectedCategory = Category.NOW_PLAYING,
                onCategorySelected = {}
            )
        }
    }
}
