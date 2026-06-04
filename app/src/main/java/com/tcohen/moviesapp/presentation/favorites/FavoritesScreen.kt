package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.OfflineBanner
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val favorites = viewModel.favoritesFlow.collectAsLazyPagingItems()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FavoritesEffect.NavigateToDetail -> onNavigateToDetail(effect.movieId)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        // Offline banner — mirrors the one on HomeScreen
        OfflineBanner(isOffline = state.isOffline)

        when (val refreshState = favorites.loadState.refresh) {
            is LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is LoadState.Error -> {
                ErrorView(
                    message = refreshState.error.message ?: "Failed to load favorites",
                    onRetry = { favorites.retry() }
                )
            }

            else -> {
                if (favorites.itemCount == 0) {
                    EmptyFavoritesState()
                } else {
                    FavoritesGrid(
                        favorites = favorites,
                        isOffline = state.isOffline,
                        onIntent = viewModel::processIntent
                    )
                }
            }
        }
    }
}

// FavoritesScreen requires Hilt injection. These previews show each UI state in isolation.

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenLoadingPreview() {
    MoviesAppTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenEmptyPreview() {
    MoviesAppTheme {
        EmptyFavoritesState()
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenErrorPreview() {
    MoviesAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            ErrorView(
                message = "Failed to load favorites. Please check your connection.",
                onRetry = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FavoritesScreenOfflineBannerPreview() {
    MoviesAppTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            OfflineBanner(isOffline = true)
            EmptyFavoritesState()
        }
    }
}
