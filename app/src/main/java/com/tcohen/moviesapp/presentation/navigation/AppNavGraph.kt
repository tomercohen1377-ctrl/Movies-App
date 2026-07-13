package com.tcohen.moviesapp.presentation.navigation

import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.tcohen.moviesapp.presentation.favorites.FavoritesScreen
import com.tcohen.moviesapp.presentation.home.HomeScreen
import com.tcohen.moviesapp.presentation.moviedetail.MovieDetailScreen
import com.tcohen.moviesapp.presentation.search.SearchScreen

@Composable
fun AppNavGraph(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in setOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Favorites.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(navController = navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Favorites.route) {
                FavoritesScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    onNavigateToDetail = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                route = Screen.MovieDetail.routeWithArgs,
                arguments = Screen.MovieDetail.arguments,
                // Slide in from the right when navigating to detail
                enterTransition = {
                    slideInHorizontally(initialOffsetX = { fullWidth -> fullWidth })
                },
                // Slide out to the right when popping back
                popExitTransition = {
                    slideOutHorizontally(targetOffsetX = { fullWidth -> fullWidth })
                },
                // Keep the host screens still (no exit animation when pushing detail)
                exitTransition = null,
                popEnterTransition = null
            ) {
                MovieDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToSimilar = { movieId ->
                        navController.navigate(Screen.MovieDetail.createRoute(movieId))
                    }
                )
            }
        }
    }
}
