package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.tcohen.moviesapp.presentation.favorites.FavoritesScreen
import com.tcohen.moviesapp.presentation.home.HomeScreen
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailScreen

/**
 * Root navigation graph.
 *
 * Structure:
 * - [Screen.Home] and [Screen.Favorites] are the two bottom-nav tabs.
 * - [Screen.MovieDetail] is reachable from both tabs and sits outside the bottom nav.
 */
@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    Scaffold(
        bottomBar = { BottomNavBar(navController = navController) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    }
                )
            }

            composable(
                route = Screen.MovieDetail.routeWithArgs,
                arguments = Screen.MovieDetail.arguments
            ) {
                MovieDetailScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
