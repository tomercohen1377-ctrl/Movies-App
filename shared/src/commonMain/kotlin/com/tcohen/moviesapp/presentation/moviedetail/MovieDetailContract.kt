package com.tcohen.moviesapp.presentation.moviedetail

import com.tcohen.moviesapp.domain.model.MovieDetail

// ── UI State (sealed interface) ───────────────────────────────────────────────

/**
 * Represents the three exclusive states of the movie detail screen.
 *
 * - [Loading] — initial fetch or reload in progress.
 * - [Error] — the network call failed; `message` is user-readable.
 * - [Success] — data is available; the FAB and content are shown.
 */
sealed interface MovieDetailUiState {
    data object Loading : MovieDetailUiState
    data class Error(val message: String) : MovieDetailUiState
    data class Success(
        val movie: MovieDetail,
        val trailerKey: String?,
        val isFavorite: Boolean = false
    ) : MovieDetailUiState
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface MovieDetailIntent {
    data object ToggleFavorite : MovieDetailIntent
    data object Reload : MovieDetailIntent
}
