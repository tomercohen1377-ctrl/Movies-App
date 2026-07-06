package com.tcohen.moviesapp.presentation.home

import com.tcohen.moviesapp.domain.model.Category

data class HomeState(
    val selectedCategory: Category = Category.NOW_PLAYING,
    val isOffline: Boolean = false
)

sealed interface HomeIntent {
    data class SelectCategory(val category: Category) : HomeIntent
    data class OpenDetail(val movieId: Int) : HomeIntent
}

sealed interface HomeEffect {
    data class NavigateToDetail(val movieId: Int) : HomeEffect
}
