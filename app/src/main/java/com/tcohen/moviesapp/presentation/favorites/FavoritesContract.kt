package com.tcohen.moviesapp.presentation.favorites

import com.tcohen.moviesapp.domain.model.Movie

// ── State ─────────────────────────────────���───────────────────────────────────

/**
 * UI state for the favorites screen.
 * The movie list itself is driven by the Paging 3 flow exposed from the ViewModel.
 * Reserved for future additions (e.g. offline banner, error message).
 */
class FavoritesState

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

// ── Effects ───────────────────────────────────────────────────────────────────

sealed interface FavoritesEffect {
    data class NavigateToDetail(val movieId: Int) : FavoritesEffect
}
