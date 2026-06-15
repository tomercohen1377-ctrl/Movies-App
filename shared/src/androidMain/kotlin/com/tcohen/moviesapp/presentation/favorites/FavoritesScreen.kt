package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.tcohen.moviesapp.domain.model.Movie
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.common.OfflineBanner

/**
 * Favorites screen — pure composable with no Hilt dependency.
 * ViewModel wiring, lifecycle collection, and effect handling are done by the caller (AppNavGraph).
 *
 * @param state Current [FavoritesState].
 * @param favorites Paged favorites list.
 * @param onIntent Dispatches [FavoritesIntent]s to the state holder.
 */
@Composable
fun FavoritesScreen(
    state: FavoritesState,
    favorites: LazyPagingItems<Movie>,
    onIntent: (FavoritesIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {

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
                        onIntent = onIntent
                    )
                }
            }
        }
    }
}
