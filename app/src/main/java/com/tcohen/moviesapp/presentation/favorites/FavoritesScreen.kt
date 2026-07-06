package com.tcohen.moviesapp.presentation.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tcohen.moviesapp.presentation.common.ErrorView
import com.tcohen.moviesapp.presentation.theme.MoviesAppTheme

@Composable
fun FavoritesScreen(
    onNavigateToDetail: (Int) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FavoritesEffect.NavigateToDetail -> onNavigateToDetail(effect.movieId)
                is FavoritesEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                FavoritesState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                FavoritesState.Empty -> EmptyFavoritesState()

                is FavoritesState.Error -> ErrorView(
                    message = current.message,
                    onRetry = { viewModel.processIntent(FavoritesIntent.Refresh) },
                )

                is FavoritesState.Success -> FavoritesGrid(
                    movies = current.movies,
                    onIntent = viewModel::processIntent,
                )
            }
        }
    }
}

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
