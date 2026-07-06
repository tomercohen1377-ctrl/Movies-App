package com.tcohen.moviesapp.presentation.favorites

import com.tcohen.moviesapp.domain.model.Movie

sealed interface FavoritesState {

    data object Loading : FavoritesState

    data class Success(val movies: List<Movie>) : FavoritesState

    data object Empty : FavoritesState

    data class Error(val httpCode: Int, val message: String) : FavoritesState
}

sealed interface FavoritesIntent {

    data class RemoveFavorite(val movie: Movie) : FavoritesIntent

    data object Refresh : FavoritesIntent

    data class OpenDetail(val movieId: Int) : FavoritesIntent
}

sealed interface FavoritesEffect {

    data class NavigateToDetail(val movieId: Int) : FavoritesEffect

    data class ShowSnackbar(val message: String) : FavoritesEffect
}
