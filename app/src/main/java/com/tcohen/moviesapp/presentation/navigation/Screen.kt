package com.tcohen.moviesapp.presentation.navigation

import androidx.navigation.NavType
import androidx.navigation.navArgument

/**
 * Sealed class representing every navigable destination in the app.
 */
sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object Search : Screen("search")

    data object Favorites : Screen("favorites")

    data object MovieDetail : Screen("movie_detail") {
        const val ARG_MOVIE_ID = "movieId"
        val routeWithArgs = "$route/{$ARG_MOVIE_ID}"
        val arguments = listOf(
            navArgument(ARG_MOVIE_ID) { type = NavType.IntType }
        )

        fun createRoute(movieId: Int) = "$route/$movieId"
    }
}
