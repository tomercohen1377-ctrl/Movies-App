package com.tcohen.moviesapp.presentation.moviedetail

import com.tcohen.moviesapp.domain.model.MovieDetail

// ── State ─────────────────────────────────────────────────────────────────────

data class MovieDetailState(
    val movie: MovieDetail? = null,
    val isFavorite: Boolean = false,
    val trailerKey: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface MovieDetailIntent {
    data object ToggleFavorite : MovieDetailIntent
    data object NavigateBack : MovieDetailIntent
    data object Reload : MovieDetailIntent
}

// ── Effects ─────────────────────────────────────────────────────────��─────────

sealed interface MovieDetailEffect {
    data object NavigateBack : MovieDetailEffect
}
