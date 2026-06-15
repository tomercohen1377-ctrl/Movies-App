package com.tcohen.moviesapp.presentation.navigation

/**
 * Sealed class representing every navigable destination in the app.
 *
 * Using string-based routes compatible with JetBrains KMP navigation-compose.
 */
sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object Favorites : Screen("favorites")

    data object MovieDetail : Screen("movie_detail") {
        const val ARG_MOVIE_ID = "movieId"
        val routeWithArgs = "$route/{$ARG_MOVIE_ID}"

        fun createRoute(movieId: Int) = "$route/$movieId"
    }
}
