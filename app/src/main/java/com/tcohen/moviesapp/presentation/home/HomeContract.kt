package com.tcohen.moviesapp.presentation.home

import com.tcohen.moviesapp.domain.model.Category

// ── State ────────────────────────────────────────────────────────────────────

data class HomeState(
    val selectedCategory: Category = Category.NOW_PLAYING,
    val isOffline: Boolean = false
)

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface HomeIntent {
    data class SelectCategory(val category: Category) : HomeIntent
    data class OpenDetail(val movieId: Int) : HomeIntent
}

// ── Effects (one-shot) ────────────────────────────────────────────────────────

sealed interface HomeEffect {
    data class NavigateToDetail(val movieId: Int) : HomeEffect
}
