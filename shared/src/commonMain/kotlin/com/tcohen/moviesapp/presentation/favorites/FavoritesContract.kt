package com.tcohen.moviesapp.presentation.favorites

import com.tcohen.moviesapp.domain.model.Movie

// ── State ────────────────────────────────────────────────────────────────────

data class FavoritesState(
    val isOffline: Boolean = false
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface FavoritesIntent {
    data class OpenDetail(val movieId: Int) : FavoritesIntent

    /**
     * Removes [movie] from favorites.
     * The full [Movie] object is required so the repository can perform
     * the local delete and server sync without an additional lookup.
     */
    data class RemoveFavorite(val movie: Movie) : FavoritesIntent
}

// ── Effects (one-shot) ────────────────────────────────────────────────────────

sealed interface FavoritesEffect {
    data class NavigateToDetail(val movieId: Int) : FavoritesEffect
}
