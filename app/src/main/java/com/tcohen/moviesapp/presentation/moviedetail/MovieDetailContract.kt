package com.tcohen.moviesapp.presentation.moviedetail

import com.tcohen.moviesapp.domain.model.MovieDetail

sealed interface MovieDetailUiState {
    data object Loading : MovieDetailUiState
    data class Error(val message: String) : MovieDetailUiState
    data class Success(
        val movie: MovieDetail,
        val trailerKey: String?,
        val isFavorite: Boolean = false
    ) : MovieDetailUiState
}

sealed interface MovieDetailIntent {
    data object ToggleFavorite : MovieDetailIntent
    data object Reload : MovieDetailIntent
}
