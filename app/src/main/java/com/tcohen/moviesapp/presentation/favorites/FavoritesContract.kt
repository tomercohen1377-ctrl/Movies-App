package com.tcohen.moviesapp.presentation.favorites

import com.tcohen.moviesapp.domain.model.Movie

// ── State ─────────────────────────────────────────────────────────────────────

data class FavoritesState(
    val favorites: List<Movie> = emptyList(),
    val isEmpty: Boolean = true
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface FavoritesIntent {
    data class OpenDetail(val movieId: Int) : FavoritesIntent
    data class RemoveFavorite(val movieId: Int) : FavoritesIntent
}

// ── Effects ───────────────────────────────────────────────────────────────────

sealed interface FavoritesEffect {
    data class NavigateToDetail(val movieId: Int) : FavoritesEffect
}
